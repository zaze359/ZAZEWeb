package com.zaze.server.feature.appmarket.service

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.zaze.server.common.utils.JsonUtil
import com.zaze.server.feature.appmarket.collector.AppMarketCollector
import com.zaze.server.feature.appmarket.dto.BatchCompleteResultVo
import com.zaze.server.feature.appmarket.dto.ExternalAppPreview
import com.zaze.server.feature.appmarket.dto.ExternalLookupVo
import com.zaze.server.feature.appmarket.dto.ExternalSearchVo
import com.zaze.server.feature.appmarket.dto.ProbeStatus
import com.zaze.server.feature.appmarket.dto.SourceGroup
import com.zaze.server.feature.appmarket.dto.SourceProbeVo
import com.zaze.server.feature.appmarket.dto.StepStatus
import com.zaze.server.feature.appmarket.model.asVo
import com.zaze.server.feature.appmarket.pojo.App
import com.zaze.server.feature.appmarket.pojo.AppVersion
import com.zaze.server.feature.appmarket.pojo.DownloadSource
import com.zaze.server.feature.appmarket.repository.AppRepository
import com.zaze.server.feature.appmarket.repository.AppVersionRepository
import com.zaze.server.feature.appmarket.repository.DownloadSourceRepository
import com.zaze.server.feature.appmarket.vo.AppVo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 外部应用查询与一键导入（搜索上游：F-Droid + IzzyOnDroid）。
 *
 * - [lookup]：按包名（或商店/官网 URL，自动提取包名）跨上游**并行**查询元数据，按优先级取首个命中（带 source 标记）；
 *   各上游独立超时，整体响应时长≈最慢单源；全部不可达或包名不存在时返回 null（由控制器转成友好的空结果）。
 * - [searchByName]：按应用名跨上游**并行**模糊搜索，各上游独立超时与失败隔离（「不同源分开算」），聚合去重
 *   （同包名只保留首个上游结果），每项带 source 标记；部分上游成功即返回结果，不再抛笼统「请稍后重试」，
 *   仅当全部上游都无结果且存在失败源时才附各失败源精确信息抛 [IllegalStateException]（由控制器转成友好提示）。
 * - [importApp]：一键把某上游的「应用 + 最新版本 + 下载源」写入三张表，并自动补全应用宝（MyApp）商店源。
 *   已存在同包名应用时抛 [IllegalArgumentException]，避免重复导入。
 *
 * IzzyOnDroid 没有 F-Droid 那样的独立名称搜索 API，其标准仓库索引 `index-v1.json`（含 apps/packages/repo
 * 全量元数据）被拉取后在内存缓存（TTL 1h，索引每日更新），搜索/查询/导入均基于该索引，避免频繁全量拉取。
 */
@Service
@CacheConfig(cacheNames = ["appmarket"])
class AppMarketExternalService(
    private val okHttpClient: OkHttpClient,
    private val cnDict: AppMarketCnDict,
    private val appRepository: AppRepository,
    private val versionRepository: AppVersionRepository,
    private val sourceRepository: DownloadSourceRepository,
    private val collector: AppMarketCollector
) {
    /** F-Droid 上游基址；可用 -Dappmarket.fdroid.base=... 覆盖（便于内网/测试环境指向镜像） */
    private val fdroidBase =
        (System.getProperty("appmarket.fdroid.base") ?: "https://f-droid.org/api/v1/packages/").let {
            if (it.endsWith("/")) it else "$it/"
        }

    /** F-Droid 按名搜索接口（官方搜索 API，服务端按 q 过滤）；可用 -Dappmarket.fdroid.search=... 覆盖 */
    private val fdroidSearchBase =
        System.getProperty("appmarket.fdroid.search") ?: "https://search.f-droid.org/api/search_apps"

    /** 按名搜索返回的最大候选数 */
    private val MAX_SEARCH_RESULTS = 30

    /** 搜索结果内存缓存（按关键词），避免频繁打 F-Droid 搜索 API 触发限流 */
    @Volatile
    private var searchCache: MutableMap<String, Pair<Long, List<FdroidSearchItemDto>>> = HashMap()
    private val SEARCH_CACHE_TTL_MS = 5 * 60 * 1000L

    /** 是否启用 F-Droid 上游（默认 true；可用 -Dappmarket.fdroid.enabled=false 关闭） */
    private val fdroidEnabled = (System.getProperty("appmarket.fdroid.enabled") ?: "true").toBoolean()

    /** IzzyOnDroid 仓库索引地址（index-v1.json，含 apps/packages/repo 全量元数据）；
     *  可用 -Dappmarket.izzy.index=... 覆盖（便于内网/测试指向镜像或本地 mock）。 */
    private val izzyIndexUrl =
        System.getProperty("appmarket.izzy.index") ?: "https://apt.izzysoft.de/fdroid/repo/index-v1.json"

    /** 是否启用 IzzyOnDroid 上游（默认 true；可用 -Dappmarket.izzy.enabled=false 关闭） */
    private val izzyEnabled = (System.getProperty("appmarket.izzy.enabled") ?: "true").toBoolean()

    /** IzzyOnDroid 索引内存缓存（TTL 1h，索引每日更新，无需频繁拉取） */
    @Volatile
    private var izzyCache: Pair<Long, IzzyIndexDto>? = null
    private val izzyLock = Any()
    private val IZZY_CACHE_TTL_MS = 60 * 60 * 1000L

    /** 外部请求超时：上游不可达/极慢时快速失败，避免查询接口长时间挂起。
     *  在注入的 OkHttpClient 基础上显式覆盖 connect/read/write/call 四类超时（双保险，
     *  避免仅设置 callTimeout 时仍被底层 readTimeout 接管导致无法快速失败）。 */
    private val extClient = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    /** IzzyOnDroid 索引较大（数 MB），首次拉取给稍长超时；成功后内存缓存 1h */
    private val izzyClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 应用宝详情页基址（**服务端渲染**：`https://sj.qq.com/appdetail/<包名>` 返回内嵌
     * `__NEXT_DATA__` 的 HTML，可按包名取到名称 / 版本名 / 大小 / 图标 / 简介 / 开发商 / 分类）。
     * 可用 -Dappmarket.myapp.base=... 覆盖。
     *
     * 历史修正：早期注释称「应用宝按名搜索抓不到、只能靠本地词典」是**错误的**——
     * 搜索页 `https://sj.qq.com/search?q=<kw>` 同样是服务端渲染（含 `__NEXT_DATA__` 与
     * 结果卡片 `href="/appdetail/<包名>"`），且页面输入时会调用官方接口
     * [myappSearchUrl]（返回结构化 JSON）。现在「应用名 → 包名」已由该官方接口承担，
     * 本地词典退化为「完全离线/接口不可达」时的兜底。
     */
    private val myappBase =
        (System.getProperty("appmarket.myapp.base") ?: "https://sj.qq.com/appdetail/").let {
            if (it.endsWith("/")) it else "$it/"
        }

    /**
     * 应用宝**按名搜索**官方接口（`POST`，Content-Type 为 `text/plain;charset=UTF-8`，请求体是 JSON 字符串）。
     * 来源于搜索页输入时的真实调用：返回 `data.components[].data.itemData[]`，
     * 每条含 `pkg_name / name / icon / version_name / apk_size / developer / cate_name_new / editor_intro`。
     *
     * 特性（实测）：中文关键词**首条即精确命中**；固定返回 Top3（`size` 参数无效，改大也无更多条目）；
     * `head.hostAppInfo.scene` 必须为 `search_result`，否则返回空列表。
     * 可用 -Dappmarket.myapp.search=... 覆盖。
     */
    private val myappSearchUrl =
        System.getProperty("appmarket.myapp.search") ?: "https://yybadaccess.3g.qq.com/v2/dc_pcyyb_official"

    /** 是否启用应用宝上游（默认 true；可用 -Dappmarket.myapp.enabled=false 关闭） */
    private val myappEnabled = (System.getProperty("appmarket.myapp.enabled") ?: "true").toBoolean()

    /** 应用宝详情页约 300KB，比 F-Droid 响应大得多，给更长超时（四类超时都要显式覆盖） */
    private val myappClient = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------ Bing 网页搜索（发现渠道：仅抽候选包名，不取 APK、不自动入库）
    /** 是否启用 Bing 发现渠道（默认 true；可用 -Dappmarket.bing.enabled=false 关闭） */
    private val bingEnabled = (System.getProperty("appmarket.bing.enabled") ?: "true").toBoolean()

    /** Bing 搜索基址（%s 为按 UTF-8 编码的查询；默认拼接 " apk" 并限定中文结果）。可用 -Dappmarket.bing.search=... 覆盖 */
    private val bingSearchBase =
        System.getProperty("appmarket.bing.search") ?: "https://www.bing.com/search?q=%s+apk&setlang=zh-CN"

    /** Bing 对 User-Agent 敏感，使用常规桌面 UA，否则易返回精简/拦截页 */
    private val BING_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    /** Bing 发现客户端（结果页较大，read 给 12s；其余沿用 extClient 形态，四类超时全显式覆盖） */
    private val bingClient = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    /** 商店域名白名单（精确 host 或其后缀）：仅从这些商店域抽取包名，彻底排除 Bing 自身的资源域名
     *  （r.bing.com / th.bing.com）、结果页元素 id（id=SERP.xxxx）以及 qq.com/mi.com 等门户的非商店子域
     *  （如 news.qq.com、img.qq.com 的 `?id=index.html` 跟踪参数）。包名抽取仅在白名单域内执行，
     *  避免把任意「x.y.z」形态的主机名/路径片段误判为包名（如 sj.qq.com 自身、id=index.html 这类值）。
     *  这里用「具体商店子域」而非「根域」(qq.com/mi.com/...)，否则会误收门户子域的噪声包名。 */
    private val STORE_HOST_SUFFIXES = setOf(
        "a.app.qq.com",          // 应用宝详情 API（?pkgname=）
        "sj.qq.com",             // 应用宝详情页（/appdetail/<pkg>）
        "app.mi.com",            // 小米应用商店（含 m.app.mi.com，?id=）
        "play.google.com",       // Google Play（?id=）
        "apps.apple.com",        // App Store
        "apkpure.com",           // APKPure（/app/<pkg>）
        "aptoide.com",           // Aptoide（store.aptoide.com）
        "f-droid.org",           // F-Droid
        "appgallery.huawei.com", // 华为应用市场
        "store.oppo.com", "oppomobile.com", // OPPO 软件商店
        "vivo.com.cn",           // vivo 应用商店
        "apps.samsung.com",      // 三星 Galaxy Store
        "wandoujia.com",         // 豌豆荚
        "coolapk.com"            // 酷安
    )

    private val searchThreadSeq = AtomicInteger(0)
    /**
     * 并行查询各上游用的线程池（守护线程）。所有网络上游并发执行、各自独立超时，
     * 整体响应时长约等于「最慢的单个上游」而非各源超时之和，避免串行等待累积导致查询超时。
     */
    private val searchExecutor: ExecutorService = Executors.newFixedThreadPool(8) { r ->
        Thread(r, "appmarket-external-${searchThreadSeq.getAndIncrement()}").also { it.isDaemon = true }
    }

    // ------------------------------------------------------------ 分源探测（各自计时 / 各自超时，互不吃预算）
    /**
     * 各上游**自身**的超时预算（ms），与各自 OkHttp 客户端的 callTimeout 对齐。
     * 关键：这些预算是「每源独立」的，不再是一条被所有源共享的总截止时间——
     * 旧实现的共享截止时间会被前面的慢源吃光，把后面已成功的上游（如应用宝）连带丢弃。
     */
    private val FDROID_TIMEOUT_MS = 10_000L
    private val IZZY_TIMEOUT_MS = 15_000L
    private val APKPURE_TIMEOUT_MS = 10_000L
    private val APTOIDE_TIMEOUT_MS = 10_000L
    private val MYAPP_TIMEOUT_MS = 20_000L
    private val BING_TIMEOUT_MS = 12_000L

    /**
     * 国外备选源总开关（F-Droid / IzzyOnDroid / APKPure / Aptoide，默认 true）。
     *
     * 这些源国内基本不可达，因此常态下**只在国内组全部未命中时才降级查询**（不是每次都查）。
     * 置为 false 则彻底不查国外组（连降级也不查），适合纯内网/无出口的部署环境。
     * 可用 -Dappmarket.overseas.enabled=false 关闭。
     */
    private val overseasEnabled = (System.getProperty("appmarket.overseas.enabled") ?: "true").toBoolean()

    /** 安全帽：整体最多等「最慢单源的自身超时 + 余量」，仅用于兜底防无限等待；
     *  它不会让快源被慢源抢走预算，且已完成的源即使超过安全帽也照常收结果。 */
    private val PROBE_SLACK_MS = 2_000L

    /** 单上游任务：自带**自己的**超时预算、请求 URL 与所属分组 */
    private data class SourceTask<T>(
        val name: String,
        val url: String?,
        val timeoutMs: Long,
        val group: SourceGroup = SourceGroup.DOMESTIC,
        val block: () -> T?
    )

    /** 单上游探测结果：结果 + 该源自身耗时 + 异常 + 是否被放弃 */
    private data class SourceProbe<T>(
        val task: SourceTask<T>,
        val result: T?,
        val elapsedMs: Long,
        val error: String?,
        val aborted: Boolean
    )

    /**
     * 并行跑所有上游：**每个源单独计时、单独超时**，互不影响。
     * 只用「最慢单源超时 + 余量」作安全帽防整体无限等待；
     * 即便超过安全帽，某源若其实已完成也照常收它的结果（不再浪费已成功的响应）。
     */
    private fun <T> probeSources(tasks: List<SourceTask<T>>): List<SourceProbe<T>> {
        if (tasks.isEmpty()) return emptyList()
        val futures = tasks.map { t ->
            searchExecutor.submit(Callable {
                val t0 = System.nanoTime()
                val run = runCatching { t.block() }
                (System.nanoTime() - t0) to run
            })
        }
        val capMs = tasks.maxOf { it.timeoutMs } + PROBE_SLACK_MS
        val cap = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(capMs)
        return futures.zip(tasks).map { (f, t) ->
            val remaining = cap - System.nanoTime()
            val got = when {
                remaining > 0 -> runCatching { f.get(remaining, TimeUnit.NANOSECONDS) }.getOrNull()
                f.isDone -> runCatching { f.get() }.getOrNull()   // 已完成就照收，不丢弃
                else -> {
                    f.cancel(true); null
                }
            }
            if (got == null) SourceProbe(t, null, t.timeoutMs, null, aborted = true)
            else {
                val (ns, run) = got
                SourceProbe(t, run.getOrNull(), ns / 1_000_000, run.exceptionOrNull()?.message, aborted = false)
            }
        }
    }

    /** 探测结果 → 对外明细（命中判定：结果非空且不是空集合） */
    private fun <T> SourceProbe<T>.toVo(): SourceProbeVo {
        val r = result
        val hit = when (r) {
            null -> false
            is Collection<*> -> r.isNotEmpty()
            else -> true
        }
        val status = when {
            aborted -> ProbeStatus.TIMEOUT
            error != null -> ProbeStatus.ERROR
            hit -> ProbeStatus.HIT
            else -> ProbeStatus.MISS
        }
        val msg = when {
            aborted -> "超过该源自身超时 ${task.timeoutMs}ms 未返回，已放弃（不影响其它源）"
            error != null -> "异常：$error"
            hit -> null
            else -> "已连通，但该源无此包名 / 无匹配项"
        }
        return SourceProbeVo(task.name, status, elapsedMs, task.url, msg, task.group)
    }

    /** 应用宝详情缓存（按包名，TTL 30min）：详情变化不频繁，避免重复抓取 300KB 页面 */
    @Volatile
    private var myappCache: MutableMap<String, Pair<Long, MyAppDetail>> = HashMap()
    private val MYAPP_CACHE_TTL_MS = 30 * 60 * 1000L

    // ------------------------------------------------------------ APKPure 上游（无官方 API，按名搜索 + 详情页解析；返回真实 APK/XAPK 直链）
    /** 是否启用 APKPure 上游（默认 true；可用 -Dappmarket.apkpure.enabled=false 关闭） */
    private val apkpureEnabled = (System.getProperty("appmarket.apkpure.enabled") ?: "true").toBoolean()

    /** APKPure 搜索页基址；可用 -Dappmarket.apkpure.search=... 覆盖 */
    private val apkpureSearchBase = System.getProperty("appmarket.apkpure.search") ?: "https://apkpure.com/search"

    /** APKPure 站点基址（详情页前缀）；可用 -Dappmarket.apkpure.base=... 覆盖 */
    private val apkpureBase = System.getProperty("appmarket.apkpure.base") ?: "https://apkpure.com"

    /** APKPure 抓取客户端（四类超时显式覆盖，10s 快速失败） */
    private val apkpureClient = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    /** APKPure 详情内存缓存（按包名，TTL 30min）：页面结构稳定，避免重复抓取 */
    @Volatile
    private var apkpureCache: MutableMap<String, Pair<Long, UpstreamAppDetail>> = HashMap()
    private val APKPURE_CACHE_TTL_MS = 30 * 60 * 1000L

    // ------------------------------------------------------------ Aptoide 上游（官方开放 API；返回真实 APK CDN 直链）
    /** 是否启用 Aptoide 上游（默认 true；可用 -Dappmarket.aptoide.enabled=false 关闭） */
    private val aptoideEnabled = (System.getProperty("appmarket.aptoide.enabled") ?: "true").toBoolean()

    /** Aptoide API 基址；搜索用 /api/2.1、详情用 /api/7；可用 -Dappmarket.aptoide.base=... 覆盖 */
    private val aptoideBase = System.getProperty("appmarket.aptoide.base") ?: "https://api.aptoide.com"

    /** Aptoide API 客户端（四类超时显式覆盖，10s 快速失败） */
    private val aptoideClient = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 按包名/链接精确查询预览（不写库）；**分两段**查询，取首个命中（带 source / group 标记）。
     *
     * 第一段只查**国内组**（应用宝）；未命中时才进入第二段查**国外备选组**
     * （F-Droid / IzzyOnDroid / APKPure / Aptoide，组内仍并行、组内按列表顺序定优先级）。
     * 这样国内应用一次查询即可返回，不必陪国外源等超时；国外专属应用仍能通过降级查到。
     *
     * 返回预览 + **逐源探测明细**（[ExternalLookupVo.probes]）：每个源各自计时、各自超时，
     * 互不吃预算，因此排在后面的源不会因为前面的源慢而被丢弃。
     */
    fun lookup(raw: String): ExternalLookupVo {
        val pkgName = normalizePackageName(raw) ?: return ExternalLookupVo(null, emptyList())
        // ---------------------------------------------------------------- 第一段：国内组（应用宝）
        // 与 searchByName 同理：国外源国内基本不可达，先查国内源，未命中才降级查国外备选组。
        val domesticTasks = mutableListOf<SourceTask<ExternalAppPreview>>()
        if (myappEnabled) domesticTasks += SourceTask(
            MYAPP_SOURCE, myappBase + pkgName, MYAPP_TIMEOUT_MS, SourceGroup.DOMESTIC
        ) {
            fetchMyAppDetail(pkgName)?.let { toMyAppPreview(it) }
        }
        val probes = mutableListOf<SourceProbeVo>()
        var preview: ExternalAppPreview? = null
        if (domesticTasks.isNotEmpty()) {
            probeSources(domesticTasks).let { ps ->
                probes += ps.map { it.toVo() }
                preview = ps.firstOrNull { it.result != null }?.result
            }
        }
        if (preview != null) return ExternalLookupVo(preview, probes, overseasQueried = false)

        // ---------------------------------------------------------------- 第二段：国外备选组
        if (!overseasEnabled) return ExternalLookupVo(null, probes, overseasQueried = false)
        val overseasTasks = mutableListOf<SourceTask<ExternalAppPreview>>()
        // 注意：每个源都带上**自己的**超时预算，而不是共享一条总截止时间
        if (fdroidEnabled) overseasTasks += SourceTask(
            "F-Droid", fdroidBase + pkgName, FDROID_TIMEOUT_MS, SourceGroup.OVERSEAS
        ) {
            fetchFdroid(pkgName)?.let { toPreview(it) }
        }
        if (izzyEnabled) overseasTasks += SourceTask(
            "IzzyOnDroid", izzyIndexUrl, IZZY_TIMEOUT_MS, SourceGroup.OVERSEAS
        ) { lookupIzzy(pkgName) }
        // APKPure / Aptoide 提供真实 APK 直链，优先级高于仅详情页的其它源
        if (apkpureEnabled) overseasTasks += SourceTask(
            APKPURE_SOURCE, "$apkpureSearchBase?q=$pkgName", APKPURE_TIMEOUT_MS, SourceGroup.OVERSEAS
        ) {
            fetchApkPure(pkgName)?.let { toUpstreamPreview(it, APKPURE_SOURCE) }
        }
        if (aptoideEnabled) overseasTasks += SourceTask(
            APTOIDE_SOURCE, "$aptoideBase/api/7/app/getMeta?package_name=$pkgName", APTOIDE_TIMEOUT_MS, SourceGroup.OVERSEAS
        ) {
            fetchAptoide(pkgName)?.let { toUpstreamPreview(it, APTOIDE_SOURCE) }
        }
        if (overseasTasks.isEmpty()) return ExternalLookupVo(null, probes, overseasQueried = false)
        // 按优先级（任务顺序）取首个命中
        probeSources(overseasTasks).let { ps ->
            probes += ps.map { it.toVo() }
            if (preview == null) {
                preview = ps.firstOrNull { it.result != null }?.result?.copy(group = SourceGroup.OVERSEAS)
            }
        }
        return ExternalLookupVo(preview, probes, overseasQueried = true)
    }

    /**
     * 按应用名跨上游模糊搜索，返回候选列表（不写库）。
     * - **各上游并行查询、各自独立超时与失败隔离**：单项上游响应慢/不可达时，不影响其他上游结果，
     *   也不整体失败；整体响应时长约等于「最慢的单个上游」，而不是各源超时之和（避免串行累积导致查询超时）。
     * - **分两段查询**：第一段只查国内组（本地词典 → 应用宝 → Bing发现兜底），**未命中才降级查**
     *   第二段国外备选组（F-Droid / IzzyOnDroid / APKPure / Aptoide）。国外源国内基本不可达且各带 5~15s
     *   超时预算，若与国内源同时并发，一次搜索要陪等到最慢的源超时（实测 15s+）——降级查询后可降到 ~1s；
     * - 本地词典排最前（即时、不依赖网络），去重时保留其来源标记；
     * - 聚合后按 packageName 去重（同包名合并为一条、缺失字段互补），再截断到 [MAX_SEARCH_RESULTS]；
     * - **部分上游成功即返回成功结果**，不再抛笼统的「请稍后重试」；
     *   仅当**全部上游都无结果**且存在失败源时，才抛出 [IllegalStateException] 并附**各失败源的精确信息**
     *   （由控制器转成友好提示，便于定位是哪个源出了问题）；全部源都可达但确实无匹配时静默返回空列表。
     */
    fun searchByName(keyword: String): ExternalSearchVo {
        val kw = keyword.trim()
        if (kw.isEmpty()) return ExternalSearchVo(emptyList(), emptyList())
        val lower = kw.lowercase()
        val probes = mutableListOf<SourceProbeVo>()
        val all = mutableListOf<ExternalAppPreview>()

        // 本地词典：同步、即时、不依赖网络；单独计时并单独记一条明细（排最前保证去重保留其来源标记）
        if (cnDict.enabled) {
            val t0 = System.nanoTime()
            val dict = runCatching { searchDict(lower) }.getOrDefault(emptyList())
            val ms = (System.nanoTime() - t0) / 1_000_000
            all += dict
            probes += SourceProbeVo(
                AppMarketCnDict.SOURCE_LABEL,
                if (dict.isNotEmpty()) ProbeStatus.HIT else ProbeStatus.MISS,
                ms, "classpath:data/appmarket_cn_dict.json",
                if (dict.isNotEmpty()) null else "词典内无匹配词条"
            )
        }

        // ---------------------------------------------------------------- 第一段：国内组（常态可达，默认只查这一组）
        // 说明：国外源国内基本不可达且各自带 5~15s 超时预算，若与国内源同时并发，
        // 一次搜索就要陪等到最慢的源超时（实测 15s+）。故国内源先查，**未命中才降级查国外组**。
        val domesticTasks = mutableListOf<SourceTask<List<ExternalAppPreview>>>()
        if (myappEnabled) domesticTasks += SourceTask(
            MYAPP_SOURCE, myappSearchUrl, MYAPP_TIMEOUT_MS, SourceGroup.DOMESTIC
        ) { searchMyApp(kw) }
        probeSources(domesticTasks).forEach { p ->
            all += p.result.orEmpty()
            probes += p.toVo()
        }

        // 合并去重：同一 packageName 合成一条（保留首个来源标记，缺失字段用后续结果补齐）
        var deduped = mergePreviews(all)
        if (deduped.isNotEmpty()) return ExternalSearchVo(deduped.take(MAX_SEARCH_RESULTS), probes)

        // 国内兜底发现：可信源都无命中时，用 Bing 网页搜索抽候选包名（仅贡献候选包名，须管理员确认才入库）。
        // Bing 同样**单独计时、单独超时**，且只在兜底时发起（不污染已知应用的搜索、不增加其网络开销）。
        if (bingEnabled) {
            val t0 = System.nanoTime()
            val bingPkgs = runCatching { discoverPackagesFromBing(kw) }.getOrDefault(emptyList())
            val ms = (System.nanoTime() - t0) / 1_000_000
            val extra = bingPkgs.map {
                ExternalAppPreview(packageName = it, name = kw, source = BING_SOURCE, apkUrl = null)
            }
            probes += SourceProbeVo(
                BING_SOURCE,
                if (extra.isNotEmpty()) ProbeStatus.HIT else ProbeStatus.MISS,
                ms, bingSearchBase.format(kw),
                if (extra.isNotEmpty()) null else "兜底发现未抽到候选包名",
                SourceGroup.DOMESTIC
            )
            if (extra.isNotEmpty()) {
                return ExternalSearchVo(mergePreviews(all + extra).take(MAX_SEARCH_RESULTS), probes)
            }
        }

        // ---------------------------------------------------------------- 第二段：国外备选组（国内组全部未命中才查）
        if (!overseasEnabled) {
            return ExternalSearchVo(emptyList(), probes, overseasQueried = false)
        }
        val overseasTasks = mutableListOf<SourceTask<List<ExternalAppPreview>>>()
        if (fdroidEnabled) overseasTasks += SourceTask(
            "F-Droid", "$fdroidSearchBase?q=$kw", FDROID_TIMEOUT_MS, SourceGroup.OVERSEAS
        ) { searchFdroid(kw, lower) }
        if (izzyEnabled) overseasTasks += SourceTask(
            "IzzyOnDroid", izzyIndexUrl, IZZY_TIMEOUT_MS, SourceGroup.OVERSEAS
        ) { searchIzzy(lower) }
        if (apkpureEnabled) overseasTasks += SourceTask(
            APKPURE_SOURCE, "$apkpureSearchBase?q=$kw", APKPURE_TIMEOUT_MS, SourceGroup.OVERSEAS
        ) { searchApkPure(kw, lower) }
        if (aptoideEnabled) overseasTasks += SourceTask(
            APTOIDE_SOURCE, "$aptoideBase/api/2.1/search/apps", APTOIDE_TIMEOUT_MS, SourceGroup.OVERSEAS
        ) { searchAptoide(kw, lower) }
        probeSources(overseasTasks).forEach { p ->
            // 国外组结果统一打上分组标记，供界面分区展示（默认 DOMESTIC，此处必须显式覆盖）
            all += p.result.orEmpty().map { it.copy(group = SourceGroup.OVERSEAS) }
            probes += p.toVo()
        }

        deduped = mergePreviews(all)
        if (deduped.isNotEmpty()) {
            return ExternalSearchVo(deduped.take(MAX_SEARCH_RESULTS), probes, overseasQueried = true)
        }
        // 全部源都无结果：不再抛笼统异常，逐源明细已随 probes 返回（界面按组、按源分别展示）
        return ExternalSearchVo(emptyList(), probes, overseasQueried = true)
    }

    /**
     * 合并去重：同一 packageName 只保留一条，**保留首个条目的来源标记**（用于溯源），
     * 但用后续同名条目**补齐缺失字段**。
     *
     * 为什么不是简单地「保留首个、丢弃后续」：本地词典排在结果最前（离线即时），
     * 但它只有包名 + 名称；应用宝随后会返回同包名的完整元数据（版本 / 图标 / 分类 / 简介）。
     * 直接丢弃会让候选列表**首条反而最不完整**（搜「微信」首条无版本号），因此改为补齐。
     */
    private fun mergePreviews(list: List<ExternalAppPreview>): List<ExternalAppPreview> {
        val merged = LinkedHashMap<String, ExternalAppPreview>()
        val noPkg = mutableListOf<ExternalAppPreview>()
        for (p in list) {
            val k = p.packageName
            if (k == null) {
                noPkg += p
                continue
            }
            val prev = merged[k]
            merged[k] = if (prev == null) p else mergePreview(prev, p)
        }
        return noPkg + merged.values
    }

    /** 以 [base] 为准（保留其 source），仅把 [base] 为 null 的字段用 [extra] 填满 */
    private fun mergePreview(base: ExternalAppPreview, extra: ExternalAppPreview): ExternalAppPreview =
        ExternalAppPreview(
            packageName = base.packageName,
            name = base.name ?: extra.name,
            summary = base.summary ?: extra.summary,
            iconSrc = base.iconSrc ?: extra.iconSrc,
            developer = base.developer ?: extra.developer,
            officialUrl = base.officialUrl ?: extra.officialUrl,
            category = base.category ?: extra.category,
            latestVersionName = base.latestVersionName ?: extra.latestVersionName,
            latestVersionCode = base.latestVersionCode ?: extra.latestVersionCode,
            sizeMb = base.sizeMb ?: extra.sizeMb,
            apkUrl = base.apkUrl ?: extra.apkUrl,
            sourceUrl = base.sourceUrl ?: extra.sourceUrl,
            source = base.source
        )

    /**
     * 是否走应用宝导入路径。
     * 「本地词典」候选只有包名、没有元数据，因此与「应用宝」来源合并处理——都由应用宝补全。
     */
    private fun isMyAppSource(source: String?): Boolean =
        source == MYAPP_SOURCE || source == AppMarketCnDict.SOURCE_LABEL || source == BING_SOURCE

    /** 本地词典上游按名搜索：只给候选包名 + 名称，元数据在导入阶段由应用宝补全 */
    private fun searchDict(lower: String): List<ExternalAppPreview> {
        return cnDict.match(lower).map {
            ExternalAppPreview(
                packageName = it.packageName,
                name = it.name,
                category = it.category,
                officialUrl = myappBase + it.packageName,
                source = AppMarketCnDict.SOURCE_LABEL
            )
        }
    }

    /** F-Droid 上游按名搜索（复用官方搜索 API + 客户端关键词兜底过滤） */
    private fun searchFdroid(keyword: String, lower: String): List<ExternalAppPreview> {
        val items = fetchSearch(keyword)
            ?: throw IllegalStateException("暂无法连接 F-Droid 搜索服务（网络不可达或接口异常）")
        return items.asSequence()
            .mapNotNull { toSearchPreview(it) }
            .filter { matchKeyword(it, lower) }
            .toList()
    }

    /** IzzyOnDroid 上游按名搜索（基于缓存的 index-v1.json 索引按关键词过滤） */
    private fun searchIzzy(lower: String): List<ExternalAppPreview> {
        val index = fetchIzzyIndex()
            ?: throw IllegalStateException("暂无法连接 IzzyOnDroid 索引服务（网络不可达或接口异常）")
        return index.apps.asSequence()
            .mapNotNull { toIzzyPreview(it, index) }
            .filter { matchKeyword(it, lower) }
            .toList()
    }

    /** 候选是否命中关键词（名称 / 简介 / 包名，忽略大小写） */
    private fun matchKeyword(p: ExternalAppPreview, lower: String): Boolean {
        return listOf(p.name, p.summary, p.packageName)
            .any { it != null && it.toString().lowercase().contains(lower) }
    }

    /**
     * 一键导入：从指定上游（默认 F-Droid）拉取「应用 + 最新版本 + 下载源」写入三张表，并自动补应用宝源。
     * [source] 为结果来源标记（"F-Droid" / "IzzyOnDroid"），为空或未知时回落到 F-Droid。
     * 命中缓存需在导入后清除，确保门户/后台列表立即可见。
     */
    @Transactional
    @CacheEvict(allEntries = true)
    fun importApp(raw: String, source: String? = null): AppVo {
        val pkgName = normalizePackageName(raw)
            ?: throw IllegalArgumentException("无法从输入中识别包名：$raw")
        ImportTracer.step("解析包名", "输入「$raw」→ 包名 $pkgName", StepStatus.OK)
        val existing = appRepository.findByPackageName(pkgName)
        ImportTracer.step(
            "路由",
            "来源=${source ?: "默认"}；库内${if (existing != null) "已存在同包名（走 upsert，不重建）" else "无记录（新建）"}",
            StepStatus.INFO
        )
        return when {
            // 「本地词典」候选只提供包名，元数据同样由应用宝补全，因此与「应用宝」走同一条 upsert 路径
            // （补全元数据 + 追加版本，可修复 seed 中的占位版本与 favicon 图标）；
            // F-Droid / Izzy 维持「已存在即拒绝」的原语义不变。
            isMyAppSource(source) -> importFromMyApp(pkgName, existing)
            // 新上游（APKPure/Aptoide）走 upsert：已存在同包名应用时仅补下载源，不重建、不拒绝
            source == APKPURE_SOURCE -> importFromApkPure(pkgName)
            source == APTOIDE_SOURCE -> importFromAptoide(pkgName)
            existing != null -> throw IllegalArgumentException("包名 $pkgName 已存在，无需重复导入")
            source == "IzzyOnDroid" -> importFromIzzy(pkgName)
            else -> importFromFdroid(pkgName)
        }
    }

    /** 从 F-Droid 导入（沿用其 /api/v1/packages/{pkg} 元数据） */
    private fun importFromFdroid(pkgName: String): AppVo {
        val pkg = fetchFdroid(pkgName)
            ?: throw IllegalArgumentException("F-Droid 未找到包名：$pkgName")
        val latest = pkg.versions?.maxByOrNull { it.added ?: 0L }
            ?: throw IllegalArgumentException("F-Droid 无可用版本：$pkgName")

        val app = appRepository.save(
            App(
                name = localized(pkg.name) ?: pkgName,
                packageName = pkg.packageName ?: pkgName,
                category = pkg.categories?.firstOrNull(),
                developer = pkg.authorName,
                summary = localized(pkg.summary),
                iconUrl = null, // 不持久化 base64 图标，门户统一用占位图标
                officialUrl = pkg.webSite
            )
        )
        val repoAddr = latest.repo?.address?.trimEnd('/') ?: "https://f-droid.org/repo"
        val apkUrl = if (!latest.apkName.isNullOrBlank()) "$repoAddr/${latest.apkName}" else null
        val version = versionRepository.save(
            AppVersion(
                appId = app.id,
                versionName = latest.versionName,
                versionCode = latest.versionCode,
                releaseDate = latest.added?.let { Date(if (it < 1_000_000_000_000L) it * 1000 else it) },
                sizeMb = latest.size?.let { it / 1024 / 1024 }
            )
        )
        if (apkUrl != null) {
            sourceRepository.save(
                DownloadSource(
                    versionId = version.id,
                    sourceName = "F-Droid",
                    sourceType = "FDROID",
                    downloadUrl = apkUrl,
                    region = "",
                    note = "从 F-Droid 自动导入"
                )
            )
        }
        // 自动补全应用宝（MyApp）商店源，与现有商店源体系一致
        collector.ensureStoreSources(app)
        return app.asVo(1)
    }

    /** 从 IzzyOnDroid 导入（基于缓存的 index-v1.json：apps 取元数据，packages 取 APK，repo 取仓库地址） */
    private fun importFromIzzy(pkgName: String): AppVo {
        val index = fetchIzzyIndex()
            ?: throw IllegalArgumentException("IzzyOnDroid 索引暂不可用，无法导入：$pkgName")
        val meta = index.apps.firstOrNull { it.packageName == pkgName }
            ?: throw IllegalArgumentException("IzzyOnDroid 未找到包名：$pkgName")
        val latest = index.packages?.get(pkgName)?.maxByOrNull { it.versionCode ?: 0L }
        val repoUrl = (index.repo?.url ?: "https://apt.izzysoft.de/fdroid/repo").trimEnd('/')
        val apkUrl = if (!latest?.apkName.isNullOrBlank()) "$repoUrl/${latest!!.apkName}" else null

        val app = appRepository.save(
            App(
                name = resolveText(meta.name) ?: pkgName,
                packageName = pkgName,
                category = meta.categories?.firstOrNull(),
                developer = meta.authorName,
                summary = resolveText(meta.summary),
                iconUrl = null,
                officialUrl = meta.webSite
            )
        )
        if (latest != null) {
            val version = versionRepository.save(
                AppVersion(
                    appId = app.id,
                    versionName = latest.versionName,
                    versionCode = latest.versionCode,
                    releaseDate = null,
                    sizeMb = latest.size?.let { it / 1024 / 1024 }
                )
            )
            if (apkUrl != null) {
                sourceRepository.save(
                    DownloadSource(
                        versionId = version.id,
                        sourceName = "IzzyOnDroid",
                        sourceType = "IZZY",
                        downloadUrl = apkUrl,
                        region = "",
                        note = "从 IzzyOnDroid 自动导入"
                    )
                )
            }
        }
        collector.ensureStoreSources(app)
        return app.asVo(1)
    }

    // ------------------------------------------------------------ 应用宝上游（国内应用元数据补全）

    /**
     * 从应用宝导入（upsert）：按包名抓取详情页元数据。
     * - 应用不存在：新建应用 + 版本 + 详情页下载源；
     * - 应用已存在：补全缺失的元数据 + 追加新版本（版本名不同才加），原有数据不丢。
     *
     * 应用宝不提供 `versionCode`，因此版本判重按**版本名**进行。
     */
    private fun importFromMyApp(pkgName: String, existing: App?): AppVo {
        val d = fetchMyAppDetail(pkgName)
            ?: throw IllegalArgumentException("应用宝未找到该包名：$pkgName")
        val app = if (existing != null) {
            val icon = existing.iconUrl
            // 只把 favicon 之类占位图标换成应用宝 CDN 图标；已是真实图标（data URI / CDN）则不动
            val placeholderIcon = icon.isNullOrBlank() ||
                icon.contains("favicon", ignoreCase = true) ||
                icon.endsWith(".ico", ignoreCase = true)
            val merged = existing.copy(
                iconUrl = if (placeholderIcon) (d.iconUrl ?: icon) else icon,
                summary = existing.summary?.takeIf { it.isNotBlank() } ?: d.summary,
                developer = existing.developer?.takeIf { it.isNotBlank() } ?: d.developer,
                category = existing.category?.takeIf { it.isNotBlank() } ?: d.category,
                officialUrl = existing.officialUrl?.takeIf { it.isNotBlank() } ?: d.sourceUrl
            )
            if (merged != existing) appRepository.save(merged) else existing
        } else {
            appRepository.save(
                App(
                    name = d.name ?: pkgName,
                    packageName = pkgName,
                    category = d.category,
                    developer = d.developer,
                    summary = d.summary,
                    iconUrl = d.iconUrl,
                    officialUrl = d.sourceUrl
                )
            )
        }
        ImportTracer.step(
            "写库-应用",
            if (existing != null) "库内已存在（id=${app.id}）→ upsert：仅补全空字段、仅替换占位图标，已有数据不丢"
            else "新建应用（id=${app.id}）：${d.name ?: pkgName}",
            StepStatus.OK
        )
        val vn = d.versionName
        var versionAdded = false
        if (!vn.isNullOrBlank() && versionRepository.findByAppIdAndVersionName(app.id, vn) == null) {
            versionRepository.save(
                AppVersion(
                    appId = app.id,
                    versionName = vn,
                    sizeMb = d.sizeMb,
                    releaseDate = d.updateTimeSec?.let { Date(it * 1000) }
                )
            )
            versionAdded = true
        }
        ImportTracer.step(
            "写库-版本",
            when {
                vn.isNullOrBlank() -> "上游未返回版本名，跳过（应用宝无 versionCode，按版本名判重）"
                versionAdded -> "新增版本 $vn（大小 ${d.sizeMb ?: "未知"}MB）"
                else -> "版本 $vn 已存在，跳过"
            },
            if (versionAdded) StepStatus.OK else StepStatus.SKIP
        )
        // 自动补全应用宝（MyApp）商店源，与现有商店源体系一致（幂等）
        collector.ensureStoreSources(app)
        return app.asVo(versionRepository.countByAppId(app.id).toInt())
    }

    // ------------------------------------------------------------ APKPure / Aptoide 导入（upsert：补下载源，不重建应用）
    /** 从 APKPure 导入（upsert）：按包名抓取详情，写入应用 + 最新版本 + 真实下载源；已存在同包名仅补源 */
    private fun importFromApkPure(pkgName: String): AppVo {
        val d = fetchApkPure(pkgName)
            ?: throw IllegalArgumentException("APKPure 未找到包名：$pkgName")
        return upsertImport(d, "APKPURE", APKPURE_SOURCE)
    }

    /** 从 Aptoide 导入（upsert）：按包名查官方 API，写入应用 + 最新版本 + 真实 APK CDN 直链；已存在同包名仅补源 */
    private fun importFromAptoide(pkgName: String): AppVo {
        val d = fetchAptoide(pkgName)
            ?: throw IllegalArgumentException("Aptoide 未找到包名：$pkgName")
        return upsertImport(d, "APTOIDE", APTOIDE_SOURCE)
    }

    /**
     * 批量补全：遍历库内所有应用，按包名逐个跑应用宝 upsert 真实元数据。
     * 复用 [importFromMyApp] 的 upsert 语义（只填空字段 / 仅替换占位图标 / 按版本名追加版本），
     * 本方法只负责「遍历 + 分类计数」。幂等、可重复执行。
     *
     * - 应用宝有该应用 → upsert；若产生了真实变更（新增版本 或 占位图标→真实图标）计入 [BatchCompleteResultVo.appsUpdated]；
     *   应用宝有数据但无变更（已补全过）计入 [appsSkipped]（无副作用）。
     * - 应用宝查不到（开源应用 / 已下架）→ 计入 [appsSkipped]，不报错。
     * - 个别异常 → 计入 [appsFailed]，不影响其余应用。
     * - [myappEnabled] 关闭时直接返回空结果，不发起任何请求。
     */
    fun batchCompleteFromMyApp(): BatchCompleteResultVo {
        if (!myappEnabled) {
            ImportTracer.step(
                "批量补全",
                "应用宝上游已关闭（-Dappmarket.myapp.enabled=false），未发起任何请求",
                StepStatus.SKIP, MYAPP_SOURCE
            )
            return BatchCompleteResultVo(appsProcessed = 0)
        }
        var processed = 0
        var updated = 0
        var skipped = 0
        var failed = 0
        val msgs = mutableListOf<String>()
        val apps = appRepository.findAll()
        val total = apps.count()
        ImportTracer.step(
            "批量补全",
            "库内共 $total 个应用：逐个执行「离线补应用宝详情页链接 → 联网抓真实元数据」",
            StepStatus.INFO, MYAPP_SOURCE
        )
        for (app in apps) {
            processed++
            val pkg = app.packageName
            if (pkg.isNullOrBlank()) {
                // 无包名的应用无法走应用宝，直接跳过（不报错）
                skipped++
                ImportTracer.step(
                    "批量补全", "[$processed/$total] 应用 id=${app.id} 无包名，跳过",
                    StepStatus.SKIP, MYAPP_SOURCE
                )
                continue
            }
            // 先离线补应用宝详情页链接（覆盖所有应用，含应用宝上没有的），再联网抓真实元数据
            collector.ensureStoreSources(app)
            try {
                val beforeVer = versionRepository.countByAppId(app.id)
                val beforeIcon = app.iconUrl
                importFromMyApp(pkg, app)
                val afterVer = versionRepository.countByAppId(app.id)
                val after = appRepository.findByPackageName(pkg)
                val changed = afterVer > beforeVer ||
                    (after != null && after.iconUrl != beforeIcon && isRealIcon(after.iconUrl))
                if (changed) updated++ else skipped++
                ImportTracer.step(
                    "批量补全",
                    "[$processed/$total] $pkg → ${if (changed) "有更新（新增版本 或 占位图标升级为真实图标）" else "无变更（已补全过，无副作用）"}",
                    if (changed) StepStatus.OK else StepStatus.SKIP, MYAPP_SOURCE
                )
            } catch (e: IllegalArgumentException) {
                // importFromMyApp 在「应用宝未找到」时抛此异常（message 含「未找到」）
                if (e.message?.contains("未找到") == true) {
                    skipped++
                    ImportTracer.step(
                        "批量补全", "[$processed/$total] $pkg → 应用宝未收录，跳过",
                        StepStatus.WARN, MYAPP_SOURCE
                    )
                } else {
                    failed++
                    if (msgs.size < 20) msgs += "${app.packageName}: ${e.message}"
                    ImportTracer.step(
                        "批量补全", "[$processed/$total] $pkg → 失败：${e.message}",
                        StepStatus.FAIL, MYAPP_SOURCE
                    )
                }
            } catch (e: Exception) {
                failed++
                if (msgs.size < 20) msgs += "${app.packageName}: ${e.message}"
                ImportTracer.step(
                    "批量补全", "[$processed/$total] $pkg → 异常：${e.message}",
                    StepStatus.FAIL, MYAPP_SOURCE
                )
            }
        }
        ImportTracer.step(
            "批量补全",
            "汇总：共处理 $processed 个 → 更新 $updated / 跳过 $skipped / 失败 $failed",
            if (failed > 0) StepStatus.WARN else StepStatus.OK, MYAPP_SOURCE
        )
        return BatchCompleteResultVo(
            appsProcessed = processed,
            appsUpdated = updated,
            appsSkipped = skipped,
            appsFailed = failed,
            messages = msgs
        )
    }

    /** 判断图标是否为真实图标（非 null/blank，且不是 favicon / .ico 占位） */
    private fun isRealIcon(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return !url.contains("favicon", ignoreCase = true) && !url.endsWith(".ico", ignoreCase = true)
    }

    /** 应用宝详情 → 预览（不写库） */
    private fun toMyAppPreview(d: MyAppDetail): ExternalAppPreview = ExternalAppPreview(
        packageName = d.packageName,
        name = d.name,
        summary = d.summary,
        iconSrc = d.iconUrl,
        developer = d.developer,
        officialUrl = d.sourceUrl,
        category = d.category,
        latestVersionName = d.versionName,
        latestVersionCode = null, // 应用宝不提供 versionCode
        sizeMb = d.sizeMb,
        apkUrl = null,            // 详情页不提供 APK 直链
        sourceUrl = d.sourceUrl,
        source = MYAPP_SOURCE
    )

    /**
     * 应用宝**按名搜索**（官方接口，POST JSON）。
     *
     * 相比抓取搜索页 HTML（800KB、需正则解析、结果里混有推荐位噪声），该接口返回结构化 JSON、
     * 体积小（约 8KB）、且**首条通常就是精确命中**，因此作为应用宝按名搜索的首选实现。
     * 返回空列表（而不是抛异常）表示未命中或不可达，交由 [probeSources] 记为 MISS/ERROR。
     */
    private fun searchMyApp(kw: String): List<ExternalAppPreview> {
        val body = myappSearchBody(kw).toRequestBody(MYAPP_JSON_MEDIA)
        val request = Request.Builder().url(myappSearchUrl)
            .header("Content-Type", "text/plain;charset=UTF-8")
            .header("Origin", "https://sj.qq.com")
            .header("Referer", "https://sj.qq.com/")
            .post(body)
            .build()
        return try {
            myappClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                parseMyAppSearch(resp.body?.string() ?: return emptyList())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 构造应用宝搜索请求体。
     * `size` 实测无效（服务端固定返回 Top3），仍保留字段以贴合真实请求；`scene` 必须为 `search_result`。
     * 关键词需做 JSON 转义（引号 / 反斜杠 / 控制字符），否则会破坏报文结构。
     */
    private fun myappSearchBody(kw: String): String {
        val safe = kw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")
        return """{"head":{"cmd":"dc_pcyyb_official","authInfo":{"businessId":"AuthName"},"deviceInfo":{"platformType":1,"platform":4},"userInfo":{"guid":"$MYAPP_SEARCH_GUID"},"expSceneIds":"","hostAppInfo":{"scene":"search_result"}},"body":{"bid":"yybhome","offset":0,"size":10,"preview":false,"listS":{"region":{"repStr":["CN"]},"keyword":{"repStr":["$safe"]}},"layout":"yybn_search_result_list"}}"""
    }

    /**
     * 解析应用宝搜索结果：`data.components[].data.itemData[]`（不写死下标，遍历所有卡片）。
     * 字段取值统一走 `isJsonPrimitive` 判定，避免 `JsonNull.asString` 抛异常。
     *
     * 说明：`apk_url` 只对 PC 端 exe 有值，Android 应用通常为空——
     * APK 直链仍需在导入阶段由 [fetchMyAppDetail] / 其它上游补全，与既有行为一致。
     */
    private fun parseMyAppSearch(json: String): List<ExternalAppPreview> {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            return emptyList()
        }
        if (root["ret"]?.takeIf { it.isJsonPrimitive }?.asInt != 0) return emptyList()
        val comps = root.getAsJsonObject("data")?.getAsJsonArray("components") ?: return emptyList()
        fun str(o: JsonObject, key: String): String? =
            o[key]?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

        val out = mutableListOf<ExternalAppPreview>()
        for (c in comps) {
            val items = c.asJsonObject.getAsJsonObject("data")?.getAsJsonArray("itemData") ?: continue
            for (node in items) {
                val o = node.asJsonObject
                val pkg = str(o, "pkg_name") ?: continue
                if (out.any { it.packageName == pkg }) continue
                out += ExternalAppPreview(
                    packageName = pkg,
                    name = str(o, "name"),
                    summary = str(o, "editor_intro"),
                    iconSrc = str(o, "icon"),
                    developer = str(o, "developer"),
                    officialUrl = myappBase + pkg,
                    category = str(o, "cate_name_new"),
                    latestVersionName = str(o, "version_name"),
                    latestVersionCode = null, // 应用宝不提供 versionCode
                    sizeMb = str(o, "apk_size")?.toLongOrNull()?.let { it / 1024 / 1024 },
                    apkUrl = str(o, "apk_url"),
                    sourceUrl = myappBase + pkg,
                    source = MYAPP_SOURCE
                )
            }
        }
        return out
    }

    /** 抓取应用宝详情（30min 内存缓存）；未启用 / 不可达 / 未找到均返回 null（不重试） */
    private fun fetchMyAppDetail(pkgName: String): MyAppDetail? {
        if (!myappEnabled) {
            ImportTracer.step("请求上游", "应用宝上游已关闭（-Dappmarket.myapp.enabled=false），跳过", StepStatus.SKIP, MYAPP_SOURCE)
            return null
        }
        val now = System.currentTimeMillis()
        myappCache[pkgName]?.let { (ts, d) ->
            if (now - ts < MYAPP_CACHE_TTL_MS) {
                ImportTracer.step(
                    "请求上游",
                    "缓存命中（${MYAPP_CACHE_TTL_MS / 1000}s 内）→ 有数据，无网络开销",
                    StepStatus.OK, MYAPP_SOURCE
                )
                return d
            }
        }
        // 真实 HTTP 请求：记录 URL、耗时与命中情况
        return ImportTracer.measure("请求上游", MYAPP_SOURCE, myappBase + pkgName) {
            val d = doFetchMyApp(pkgName)
            if (d != null) synchronized(this) { myappCache[pkgName] = now to d }
            d
        }
    }

    private fun doFetchMyApp(pkgName: String): MyAppDetail? {
        val request = Request.Builder().url(myappBase + pkgName)
            .header("User-Agent", "zaze-appmarket-external")
            .get().build()
        return try {
            myappClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null
                else parseMyApp(resp.body?.string() ?: return null, pkgName)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析应用宝详情页：取出内嵌的 `__NEXT_DATA__` JSON，递归找 `pkg_name == pkgName` 的对象。
     * 不写死 `components[i].data.itemData[j]` 这类路径，页面结构调整时更抗变。
     */
    private fun parseMyApp(html: String, pkgName: String): MyAppDetail? {
        val m = Regex("id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html) ?: return null
        val root = try {
            JsonParser.parseString(m.groupValues[1]).asJsonObject
        } catch (e: Exception) {
            return null
        }
        val item = findPkgNode(root, pkgName) ?: return null
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
            item[k]?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
        }
        fun long(key: String): Long? =
            item[key]?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.toLongOrNull()
        return MyAppDetail(
            packageName = pkgName,
            name = str("name"),
            summary = str("description", "editor_intro")?.take(500),
            iconUrl = str("icon"),
            developer = str("developer", "operator"),
            category = str("cate_name", "cate_name_new"),
            versionName = str("version_name"),
            sizeMb = long("apk_size")?.let { it / 1024 / 1024 },
            updateTimeSec = long("update_time"),
            sourceUrl = myappBase + pkgName
        )
    }

    /** 递归查找 `pkg_name` 等于目标包名的 JsonObject */
    private fun findPkgNode(node: JsonElement, pkg: String): JsonObject? {
        when {
            node.isJsonObject -> {
                val obj = node.asJsonObject
                if (obj["pkg_name"]?.takeIf { it.isJsonPrimitive }?.asString == pkg) return obj
                for ((_, v) in obj.entrySet()) {
                    findPkgNode(v, pkg)?.let { return it }
                }
            }
            node.isJsonArray -> {
                for (v in node.asJsonArray) {
                    findPkgNode(v, pkg)?.let { return it }
                }
            }
        }
        return null
    }

    private fun toPreview(pkg: FdroidPackageDto): ExternalAppPreview {
        val pkgName = pkg.packageName ?: ""
        val latest = pkg.versions?.maxByOrNull { it.added ?: 0L }
        val repoAddr = latest?.repo?.address?.trimEnd('/') ?: "https://f-droid.org/repo"
        val apkUrl = if (!latest?.apkName.isNullOrBlank()) "$repoAddr/${latest!!.apkName}" else null
        return ExternalAppPreview(
            packageName = pkg.packageName,
            name = localized(pkg.name) ?: pkgName,
            summary = localized(pkg.summary),
            iconSrc = pkg.icon,
            developer = pkg.authorName,
            officialUrl = pkg.webSite,
            category = pkg.categories?.firstOrNull(),
            latestVersionName = latest?.versionName,
            latestVersionCode = latest?.versionCode,
            sizeMb = latest?.size?.let { it / 1024 / 1024 },
            apkUrl = apkUrl,
            sourceUrl = "https://f-droid.org/packages/$pkgName",
            source = "F-Droid"
        )
    }

    private fun fetchFdroid(packageName: String): FdroidPackageDto? {
        val url = fdroidBase + packageName
        // 已为外部请求设置硬性超时（extClient 10s），上游不可达会快速失败；
        // 此处单次请求即可，重试对「超时型不可达」无意义，仅徒增等待。
        return ImportTracer.measure("请求上游", "F-Droid", url) { doFetch(url) }
    }

    private fun fetchSearch(keyword: String): List<FdroidSearchItemDto>? {
        val q = URLEncoder.encode(keyword, "UTF-8")
        val cacheKey = keyword.trim().lowercase()
        val now = System.currentTimeMillis()
        synchronized(searchCache) {
            val hit = searchCache[cacheKey]
            if (hit != null && now - hit.first < SEARCH_CACHE_TTL_MS) return hit.second
        }
        val candidates = mutableListOf<String>().apply {
            add("$fdroidSearchBase?q=$q")
            // 兼容旧配置：若主地址指向 f-droid.org 的 search.json（静态全量索引 / 已失效），自动回退到官方搜索 API
            if (fdroidSearchBase.contains("f-droid.org") && fdroidSearchBase.contains("search.json")) {
                add("https://search.f-droid.org/api/search_apps?q=$q")
            }
        }
        for (url in candidates) {
            // 已为外部请求设置硬性超时（extClient 10s），单次请求即可；
            // 上游不可达会快速失败（返回 null），不在此处重试以免叠加超时。
            val items = doFetchSearch(url)
            if (items != null) {
                synchronized(searchCache) { searchCache[cacheKey] = now to items }
                return items
            }
        }
        return null
    }

    private fun doFetchSearch(url: String): List<FdroidSearchItemDto>? {
        val request = Request.Builder().url(url)
            .header("User-Agent", "zaze-appmarket-external")
            .header("Accept", "application/json")
            .get().build()
        return try {
            extClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null
                else {
                    val body = resp.body?.string() ?: return null
                    parseSearchBody(body)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 兼容 F-Droid 搜索 API 的多种返回结构：
     * 1) 顶层数组 [...]；2) 顶层对象且首个数组字段为结果（如 {results:[...]}）；3) 单个对象。
     * 解析成功但集合为空（如 []）也返回空列表，以区分「无匹配」与「解析失败」。
     */
    private fun parseSearchBody(body: String): List<FdroidSearchItemDto>? {
        val gson = Gson()
        // 1) 直接是数组
        runCatching { gson.fromJson(body, Array<FdroidSearchItemDto>::class.java)?.toList() }
            .getOrNull()?.let { if (it.isNotEmpty() || body.trim() == "[]") return it }
        // 2) 被对象包裹：取第一个数组类型字段
        runCatching {
            JsonParser.parseString(body).asJsonObject
                .entrySet().firstOrNull { it.value.isJsonArray }
                ?.value?.asJsonArray
                ?.let { gson.fromJson(it, Array<FdroidSearchItemDto>::class.java)?.toList() }
        }.getOrNull()?.let { return it }
        // 3) 顶层就是单个对象
        runCatching { gson.fromJson(body, FdroidSearchItemDto::class.java) }
            .getOrNull()?.let { return listOf(it) }
        return null
    }

    private fun toSearchPreview(item: FdroidSearchItemDto): ExternalAppPreview? {
        val pkg = item.packageName ?: return null
        val icon = item.icon?.let { if (it.startsWith("http")) it else "https://f-droid.org/repo/$it" }
        return ExternalAppPreview(
            packageName = pkg,
            name = resolveText(item.name) ?: pkg,
            summary = resolveText(item.summary),
            iconSrc = icon,
            developer = item.author,
            category = item.categories?.firstOrNull(),
            latestVersionName = item.version,
            source = "F-Droid"
        )
    }

    // ------------------------------------------------------------ IzzyOnDroid 上游
    /** 按包名从 IzzyOnDroid 索引精确查询单个应用预览 */
    private fun lookupIzzy(pkgName: String): ExternalAppPreview? {
        val index = fetchIzzyIndex() ?: return null
        val meta = index.apps.firstOrNull { it.packageName == pkgName } ?: return null
        return toIzzyPreview(meta, index)
    }

    /** 把 IzzyOnDroid 索引中的应用条目映射为外部预览（带 source 标记） */
    private fun toIzzyPreview(app: IzzyAppDto, index: IzzyIndexDto): ExternalAppPreview? {
        val pkg = app.packageName ?: return null
        val pkgs = index.packages?.get(pkg)
        val latest = pkgs?.maxByOrNull { it.versionCode ?: 0L }
        val repoUrl = (index.repo?.url ?: "https://apt.izzysoft.de/fdroid/repo").trimEnd('/')
        val apkUrl = if (!latest?.apkName.isNullOrBlank()) "$repoUrl/${latest!!.apkName}" else null
        val icon = app.icon?.let { if (it.startsWith("http")) it else "$repoUrl/icons/$it" }
        return ExternalAppPreview(
            packageName = pkg,
            name = resolveText(app.name) ?: pkg,
            summary = resolveText(app.summary),
            iconSrc = icon,
            developer = app.authorName,
            officialUrl = app.webSite,
            category = app.categories?.firstOrNull(),
            latestVersionName = latest?.versionName,
            latestVersionCode = latest?.versionCode,
            sizeMb = latest?.size?.let { it / 1024 / 1024 },
            apkUrl = apkUrl,
            sourceUrl = "https://apt.izzysoft.de/fdroid/packages/$pkg",
            source = "IzzyOnDroid"
        )
    }

    /** 拉取并缓存 IzzyOnDroid 仓库索引（index-v1.json）；失败返回 null */
    private fun fetchIzzyIndex(): IzzyIndexDto? {
        val now = System.currentTimeMillis()
        synchronized(izzyLock) {
            val hit = izzyCache
            if (hit != null && now - hit.first < IZZY_CACHE_TTL_MS) {
                ImportTracer.step(
                    "请求上游",
                    "索引缓存命中（${IZZY_CACHE_TTL_MS / 60_000}min 内）→ 可用，无网络开销",
                    StepStatus.OK, "IzzyOnDroid"
                )
                return hit.second
            }
        }
        // 索引较大（数 MB），首次拉取耗时长，记录 URL 与耗时
        return ImportTracer.measure("请求上游", "IzzyOnDroid", izzyIndexUrl) {
            try {
                val request = Request.Builder().url(izzyIndexUrl)
                    .header("User-Agent", "zaze-appmarket-external")
                    .header("Accept", "application/json")
                    .get().build()
                izzyClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else {
                        val body = resp.body?.string() ?: return@measure null
                        val dto = Gson().fromJson(body, IzzyIndexDto::class.java)
                        synchronized(izzyLock) { izzyCache = now to dto }
                        dto
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Bing 网页搜索「发现」：给定应用名，从 cn.bing.com 结果页提取候选包名。
     * - 仅贡献候选包名，不解析元数据、不下载 APK、不写入下载源；真正的元数据/APK 由调用方再交给既有可信上游（lookup/importApp）。
     * - 仅从结果 URL 中抽取（包名来自商店 URL 参数 `pkgname=`/`id=` 或路径段），噪声域名后缀黑名单过滤；
     *   候选再经 [PACKAGE_RE] 校验 + 去重 + 截断。
     * - 任意失败（不可达/被拦/解析异常）返回空列表，由调用方静默降级。
     */
    private fun discoverPackagesFromBing(keyword: String): List<String> {
        if (!bingEnabled) return emptyList()
        val url = bingSearchBase.format(URLEncoder.encode(keyword, "UTF-8"))
        val request = Request.Builder().url(url)
            .header("User-Agent", BING_UA)
            .get().build()
        val html = try {
            bingClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            return emptyList()
        }
        if (html.isEmpty()) return emptyList()
        // 先解 HTML 实体（Bing 的 &amp; 等），否则 query 参数分隔符解析会失真
        val decoded = html.replace("&amp;", "&")
        val found = linkedSetOf<String>()
        for (m in Regex("""href=["']([^"']+)["']""").findAll(decoded)) {
            val link = m.groupValues[1]
            val host = hostOf(link) ?: continue
            // 仅处理已知商店域（白名单）：Bing 资源域 / 政府门户 / 百科等一律跳过，
            // 杜绝把主机名或任意「x.y.z」路径片段误判为包名（如 r.bing.com、sj.qq.com 自身、id=SERP.xxxx）
            if (!STORE_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) continue
            pkgFromUrl(link)?.let { if (PACKAGE_RE.matches(it)) found += it }
        }
        return found.take(MAX_SEARCH_RESULTS)
    }

    /** 提取链接 host（小写）；相对链接返回 null */
    private fun hostOf(link: String): String? =
        Regex("""https?://([^/?#"'\s]+)""").find(link)?.groupValues?.get(1)?.lowercase()

    /** 从链接抽取包名：仅对已知商店域（见 [STORE_HOST_SUFFIXES]）调用，因此可直接信任形态。
     *  优先商店 URL 查询参数 `?pkgname=<pkg>` / `?id=<pkg>`，其次已知商店路径
     *  `/appdetail/<pkg>` / `/details/<pkg>` / `/apps/details/<pkg>`；不再做裸「/com.foo.bar」兜底，
     *  避免把主机名（如 sj.qq.com）或任意路径片段误判为包名。 */
    private fun pkgFromUrl(link: String): String? {
        Regex("""(?:[?&]|&amp;)(?:pkgname|id)=([a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+)""")
            .find(link)?.let { return it.groupValues[1] }
        Regex("""/(?:appdetail|details|apps/details|app)/([a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+)""")
            .find(link)?.let { return it.groupValues[1] }
        return null
    }

    /** 列出所有已配置的搜索上游（供管理端 UI 展示「所有源」） */
    fun listSearchProviders(): List<SearchProviderMeta> = listOf(
        SearchProviderMeta("fdroid", "F-Droid", "https://search.f-droid.org/api/search_apps", fdroidEnabled),
        SearchProviderMeta("izzy", "IzzyOnDroid", izzyIndexUrl, izzyEnabled),
        SearchProviderMeta(
            "dict", AppMarketCnDict.SOURCE_LABEL, "classpath:${AppMarketCnDict.DICT_PATH}", cnDict.enabled
        ),
        SearchProviderMeta(
            "myapp", MYAPP_SOURCE, "$myappSearchUrl（按名搜索）/ $myappBase（详情页补全）", myappEnabled
        ),
        SearchProviderMeta("apkpure", APKPURE_SOURCE, apkpureSearchBase, apkpureEnabled),
        SearchProviderMeta("aptoide", APTOIDE_SOURCE, "$aptoideBase/api/2.1/search/apps", aptoideEnabled),
        SearchProviderMeta("bing", BING_SOURCE, bingSearchBase, bingEnabled)
    )

    /** 兼容 F-Droid 字段既可能是字符串，也可能是 {locale: text} 对象 */
    private fun resolveText(v: Any?): String? {
        return when (v) {
            is String -> v.takeIf { it.isNotBlank() }
            is Map<*, *> -> {
                val m = v.mapKeys { (it.key ?: "").toString().lowercase() }
                for (k in listOf("zh-cn", "zh", "zh-hans", "en", "en-us")) {
                    val valStr = m[k]
                    if (valStr is String && valStr.isNotBlank()) return valStr
                }
                m.values.firstOrNull() as? String
            }
            else -> null
        }
    }

    private fun doFetch(url: String): FdroidPackageDto? {
        val request = Request.Builder().url(url)
            .header("User-Agent", "zaze-appmarket-external")
            .get().build()
        return try {
            extClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null
                else {
                    val body = resp.body?.string() ?: return null
                    JsonUtil.parseJson(body, FdroidPackageDto::class.java)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 从包名或商店/官网 URL 中规范出包名；无法识别返回 null */
    private fun normalizePackageName(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (PACKAGE_RE.matches(s)) return s
        val idMatch = Regex("[?&]id=([^&/]+)").find(s)
        if (idMatch != null) return idMatch.groupValues[1].trim()
        val pkgMatch = Regex("/packages/([^/]+)").find(s)
        if (pkgMatch != null) return pkgMatch.groupValues[1].trim()
        if (s.contains("/")) {
            val seg = s.trimEnd('/').substringAfterLast('/')
            if (PACKAGE_RE.matches(seg)) return seg
        }
        return null
    }

    private fun localized(map: Map<String, String>?): String? {
        if (map.isNullOrEmpty()) return null
        val preferred = listOf("zh-CN", "zh", "zh-Hans", "en", "en-US")
        for (k in preferred) {
            val v = map[k] ?: map[k.lowercase()]
            if (!v.isNullOrBlank()) return v
        }
        return map.values.firstOrNull()
    }

    /**
     * 应用宝详情页解析结果（仅取所需字段）。
     *
     * 应用宝只提供 `version_name` + `update_time` + `md_5`，**没有 versionCode**，
     * 也没有 APK 直链（`apk_url` / `download_url` 为空），因此只能落详情页下载源。
     */
    private data class MyAppDetail(
        val packageName: String,
        val name: String? = null,
        val summary: String? = null,
        val iconUrl: String? = null,
        val developer: String? = null,
        val category: String? = null,
        val versionName: String? = null,
        val sizeMb: Long? = null,
        val updateTimeSec: Long? = null,
        val sourceUrl: String? = null
    )

    // ------------------------------------------------------------ 上游统一模型（APKPure / Aptoide 共用）
    /** 上游返回的「应用 + 真实下载源」统一结构：解析后映射为 ExternalAppPreview，或写入三张表 */
    private data class UpstreamAppDetail(
        val packageName: String,
        val name: String? = null,
        val iconUrl: String? = null,
        val versionName: String? = null,
        val versionCode: Long? = null,
        val sizeMb: Long? = null,
        val downloadUrl: String? = null,
        val sourceUrl: String? = null
    )

    private fun toUpstreamPreview(d: UpstreamAppDetail, source: String): ExternalAppPreview = ExternalAppPreview(
        packageName = d.packageName,
        name = d.name,
        summary = null,
        iconSrc = d.iconUrl,
        developer = null,
        officialUrl = d.sourceUrl,
        category = null,
        latestVersionName = d.versionName,
        latestVersionCode = d.versionCode,
        sizeMb = d.sizeMb,
        apkUrl = d.downloadUrl,
        sourceUrl = d.sourceUrl,
        source = source
    )

    /**
     * upsert 导入：已存在同包名应用则复用，仅按 downloadUrl 去重追加**真实**下载源；
     * 不存在则新建应用 + 版本 + 下载源。版本按 versionName 判重（幂等）。
     */
    private fun upsertImport(d: UpstreamAppDetail, sourceType: String, sourceName: String): AppVo {
        val pkg = d.packageName
        val existedApp = appRepository.findByPackageName(pkg)
        val app = existedApp ?: appRepository.save(
            App(
                name = d.name ?: pkg,
                packageName = pkg,
                category = null,
                developer = null,
                summary = null,
                iconUrl = d.iconUrl,
                officialUrl = d.sourceUrl
            )
        )
        ImportTracer.step(
            "写库-应用",
            if (existedApp != null) "库内已存在（id=${app.id}）→ upsert：仅补下载源，不重建"
            else "新建应用（id=${app.id}）：${d.name ?: pkg}",
            StepStatus.OK
        )
        val versionName = d.versionName
        var versionAdded = false
        val version = if (!versionName.isNullOrBlank()) {
            versionRepository.findByAppIdAndVersionName(app.id, versionName) ?: run {
                versionAdded = true
                versionRepository.save(
                    AppVersion(
                        appId = app.id,
                        versionName = versionName,
                        versionCode = d.versionCode,
                        sizeMb = d.sizeMb
                    )
                )
            }
        } else null
        ImportTracer.step(
            "写库-版本",
            when {
                versionName.isNullOrBlank() -> "上游未返回版本名，跳过"
                versionAdded -> "新增版本 $versionName（versionCode=${d.versionCode ?: "未知"}）"
                else -> "版本 $versionName 已存在，复用"
            },
            if (versionAdded) StepStatus.OK else StepStatus.SKIP
        )
        val downloadUrl = d.downloadUrl
        var sourceAdded = false
        if (!downloadUrl.isNullOrBlank() && version != null &&
            sourceRepository.findByVersionIdAndDownloadUrl(version.id, downloadUrl) == null
        ) {
            sourceRepository.save(
                DownloadSource(
                    versionId = version.id,
                    sourceName = sourceName,
                    sourceType = sourceType,
                    downloadUrl = downloadUrl,
                    region = "",
                    note = "从 $sourceName 自动导入"
                )
            )
            sourceAdded = true
        }
        ImportTracer.step(
            "写库-下载源",
            when {
                downloadUrl.isNullOrBlank() -> "上游未返回 APK 直链，跳过"
                version == null -> "无可用版本，跳过写入下载源"
                sourceAdded -> "新增 $sourceName 直链：$downloadUrl"
                else -> "该直链已存在，跳过（按 downloadUrl 去重）"
            },
            if (sourceAdded) StepStatus.OK else StepStatus.SKIP,
            sourceName
        )
        collector.ensureStoreSources(app)
        return app.asVo(versionRepository.countByAppId(app.id).toInt())
    }

    // ------------------------------------------------------------ APKPure 上游（无官方 API，抓取实现）
    /** 按包名查 APKPure 详情（30min 内存缓存）；未启用 / 不可达 / 未找到均返回 null（不重试） */
    private fun fetchApkPure(pkgName: String): UpstreamAppDetail? {
        if (!apkpureEnabled) {
            ImportTracer.step("请求上游", "APKPure 上游已关闭（-Dappmarket.apkpure.enabled=false），跳过", StepStatus.SKIP, APKPURE_SOURCE)
            return null
        }
        val now = System.currentTimeMillis()
        apkpureCache[pkgName]?.let { (ts, d) ->
            if (now - ts < APKPURE_CACHE_TTL_MS) {
                ImportTracer.step(
                    "请求上游",
                    "缓存命中（${APKPURE_CACHE_TTL_MS / 1000}s 内）→ 有数据，无网络开销",
                    StepStatus.OK, APKPURE_SOURCE
                )
                return d
            }
        }
        // 详情页 URL 含类目前缀、无法由包名直接拼出，需先搜索定位再抓详情（两步请求合并记一次）
        return ImportTracer.measure("请求上游", APKPURE_SOURCE, "$apkpureSearchBase?q=$pkgName（搜索定位 → 详情页）") {
            val d = doFetchApkPure(pkgName)
            if (d != null) synchronized(this) { apkpureCache[pkgName] = now to d }
            d
        }
    }

    /**
     * APKPure 详情页 URL 含类目前缀（/&lt;category&gt;/&lt;pkg&gt;/），无法直接由包名拼出，
     * 因此先按包名搜索定位精确匹配项，再抓详情页取真实直链。
     */
    private fun doFetchApkPure(pkgName: String): UpstreamAppDetail? {
        val searchUrl = "$apkpureSearchBase?q=${URLEncoder.encode(pkgName, "UTF-8")}"
        val relUrl = try {
            apkpureClient.newCall(
                Request.Builder().url(searchUrl).header("User-Agent", "zaze-appmarket-external").get().build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) null else findApkPureDetailUrl(resp.body?.string() ?: return null, pkgName)
            }
        } catch (e: Exception) { null } ?: return null
        val detailUrl = if (relUrl.startsWith("http")) relUrl else "$apkpureBase$relUrl"
        return try {
            apkpureClient.newCall(
                Request.Builder().url(detailUrl).header("User-Agent", "zaze-appmarket-external").get().build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) null else parseApkPureDetail(resp.body?.string() ?: return null, pkgName)
            }
        } catch (e: Exception) { null }
    }

    /** 从搜索结果 HTML 中提取匹配包名的详情页相对路径 */
    private fun findApkPureDetailUrl(html: String, pkgName: String): String? {
        val re = Regex("""href="(/[^\s"']*?/${java.util.regex.Pattern.quote(pkgName)}/?)"""")
        return re.find(html)?.groupValues?.getOrNull(1)
    }

    /** 解析 APKPure 详情页：优先取页面内 d.apkpure.com 真实直链，否则回退规范 XAPK 直链 */
    private fun parseApkPureDetail(html: String, pkgName: String): UpstreamAppDetail? {
        val linkRaw = Regex("""d\.apkpure\.com/b/(?:APK|XAPK)/[^\s"'<>]+""").find(html)?.value
        val downloadUrl = when {
            linkRaw == null -> "https://d.apkpure.com/b/XAPK/$pkgName?version=latest"
            linkRaw.startsWith("//") -> "https:$linkRaw"
            else -> linkRaw
        }
        fun tag(re: Regex): String? =
            re.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        val name = tag(Regex("""<h1[^>]*>([^<]+)</h1>""")) ?: tag(Regex("""class="title"[^>]*>([^<]+)</"""))
        val version = tag(Regex("""Version\s*<[^>]*>([^<]+)</"""))
        val icon = tag(Regex("""<meta property="og:image"[^>]*content="([^"]+)"""))
            ?: tag(Regex("""class="icon"[^>]*src="([^"]+)"""))
        return UpstreamAppDetail(pkgName, name, icon, version, null, null, downloadUrl, "$apkpureBase/$pkgName/")
    }

    private fun searchApkPure(keyword: String, lower: String): List<ExternalAppPreview> {
        val items = fetchApkPureSearch(keyword)
            ?: throw IllegalStateException("暂无法连接 APKPure 搜索服务（网络不可达或接口异常）")
        return items.asSequence().mapNotNull { toUpstreamPreview(it, APKPURE_SOURCE) }
            .filter { matchKeyword(it, lower) }.toList()
    }

    private fun fetchApkPureSearch(keyword: String): List<UpstreamAppDetail>? {
        val url = "$apkpureSearchBase?q=${URLEncoder.encode(keyword, "UTF-8")}"
        return try {
            apkpureClient.newCall(
                Request.Builder().url(url).header("User-Agent", "zaze-appmarket-external").get().build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) null else parseApkPureSearch(resp.body?.string() ?: return null)
            }
        } catch (e: Exception) { null }
    }

    /** 解析 APKPure 搜索页：结果块形如 &lt;a href="/&lt;category&gt;/&lt;pkg&gt;/"&gt;，内含标题与图标 */
    private fun parseApkPureSearch(html: String): List<UpstreamAppDetail> {
        val results = mutableListOf<UpstreamAppDetail>()
        val seen = mutableSetOf<String>()
        val re = Regex("""(?s)<a[^>]+href="(/[^\s"']*?/(com\.[a-zA-Z0-9_.]+)/?)"[^>]*>(.*?)</a>""")
        for (m in re.findAll(html)) {
            val rel = m.groupValues[1]
            val pkg = m.groupValues[2]
            if (!seen.add(pkg)) continue
            val block = m.groupValues[3]
            fun inBlock(r: Regex): String? =
                r.find(block)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            val name = inBlock(Regex("""<div class="name"[^>]*>([^<]+)</div>"""))
                ?: inBlock(Regex("""title="([^"]+)""""))
            val icon = inBlock(Regex("""<img[^>]+src="(https?://[^"]+)""""))
            results += UpstreamAppDetail(
                packageName = pkg,
                name = name ?: pkg,
                iconUrl = icon,
                versionName = null,
                versionCode = null,
                sizeMb = null,
                downloadUrl = "https://d.apkpure.com/b/XAPK/$pkg?version=latest",
                sourceUrl = "$apkpureBase$rel"
            )
        }
        return results
    }

    // ------------------------------------------------------------ Aptoide 上游（官方开放 API）
    /** 按包名查 Aptoide getMeta；未启用 / 不可达 / 未找到均返回 null（不重试） */
    private fun fetchAptoide(pkgName: String): UpstreamAppDetail? {
        if (!aptoideEnabled) {
            ImportTracer.step("请求上游", "Aptoide 上游已关闭（-Dappmarket.aptoide.enabled=false），跳过", StepStatus.SKIP, APTOIDE_SOURCE)
            return null
        }
        val url = "$aptoideBase/api/7/app/getMeta?package_name=${URLEncoder.encode(pkgName, "UTF-8")}"
        return ImportTracer.measure("请求上游", APTOIDE_SOURCE, url) {
            try {
                aptoideClient.newCall(
                    Request.Builder().url(url).header("User-Agent", "zaze-appmarket-external").get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) null else parseAptoideMeta(resp.body?.string() ?: return@measure null, pkgName)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** 解析 Aptoide getMeta：file.path 即真实 APK CDN 直链；兼容 `{...}` 与 `{data:{...}}` 两种包裹 */
    private fun parseAptoideMeta(body: String, pkgName: String): UpstreamAppDetail? {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        val obj = if (root.has("file") || root.has("package")) root
        else if (root.has("data") && root["data"].isJsonObject) root["data"].asJsonObject else return null
        val file = obj["file"]?.takeIf { it.isJsonObject }?.asJsonObject
        val path = file?.get("path")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        fun str(vararg ks: String): String? = ks.firstNotNullOfOrNull { k ->
            obj[k]?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
        }
        val name = str("name")
        val icon = str("icon")
        // Elvis 返回后 file 已智能转为非空（若为空则在 path 处提前返回），此处无需再安全调用
        val vername = file.get("vername")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
        val vercode = file.get("vercode")?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
        val size = file.get("filesize")?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
            ?.let { it / 1024 / 1024 }
        return UpstreamAppDetail(
            pkgName, name, icon, vername, vercode, size, path,
            "$aptoideBase/app/getMeta?package_name=$pkgName"
        )
    }

    private fun searchAptoide(keyword: String, lower: String): List<ExternalAppPreview> {
        val items = fetchAptoideSearch(keyword)
            ?: throw IllegalStateException("暂无法连接 Aptoide 搜索服务（网络不可达或接口异常）")
        return items.asSequence().mapNotNull { toUpstreamPreview(it, APTOIDE_SOURCE) }
            .filter { matchKeyword(it, lower) }.toList()
    }

    private fun fetchAptoideSearch(keyword: String): List<UpstreamAppDetail>? {
        val url = "$aptoideBase/api/2.1/search/apps?query=${URLEncoder.encode(keyword, "UTF-8")}&limit=$MAX_SEARCH_RESULTS"
        return try {
            aptoideClient.newCall(
                Request.Builder().url(url).header("User-Agent", "zaze-appmarket-external").get().build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) null else parseAptoideSearch(resp.body?.string() ?: return null)
            }
        } catch (e: Exception) { null }
    }

    /** 解析 Aptoide 搜索：datalist.list[] 每项的 file.path 即真实 APK CDN 直链 */
    private fun parseAptoideSearch(body: String): List<UpstreamAppDetail>? {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        val list = root["datalist"]?.takeIf { it.isJsonObject }?.asJsonObject?.get("list")
            ?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        return list.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val obj = el.asJsonObject
            val pkg = obj["package"]?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val file = obj["file"]?.takeIf { it.isJsonObject }?.asJsonObject
            val path = file?.get("path")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            fun str(vararg ks: String): String? = ks.firstNotNullOfOrNull { k ->
                obj[k]?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
            }
            val name = str("name")
            val icon = str("icon")
            // Elvis 返回后 file 已智能转为非空（若为空则在 path 处提前返回），此处无需再安全调用
            val vername = file.get("vername")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            val vercode = file.get("vercode")?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
            val size = file.get("filesize")?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
                ?.let { it / 1024 / 1024 }
            UpstreamAppDetail(
                pkg, name, icon, vername, vercode, size, path,
                "$aptoideBase/app/getMeta?package_name=$pkg"
            )
        }
    }

    companion object {
        private val PACKAGE_RE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+\$")

        /** 上游来源标记：应用宝（腾讯） */
        const val MYAPP_SOURCE = "应用宝"
        /** 上游来源标记：APKPure（按名搜索 + 真实 APK/XAPK 直链；无官方 API，抓取实现） */
        const val APKPURE_SOURCE = "APKPure"
        /** 上游来源标记：Aptoide（官方开放 API + 真实 APK CDN 直链） */
        const val APTOIDE_SOURCE = "Aptoide"
        /** 上游来源标记：Bing 网页搜索（发现渠道：仅抽候选包名，不取 APK、不自动入库） */
        const val BING_SOURCE = "Bing发现"

        /** 应用宝搜索接口的 Content-Type：官方要求 `text/plain;charset=UTF-8`（不是 application/json） */
        private val MYAPP_JSON_MEDIA = "text/plain;charset=UTF-8".toMediaType()

        /**
         * 应用宝搜索接口所需的设备 guid（任意 UUID 实测均可，服务端不做校验）。
         * 固定值即可，避免每次随机导致服务端认为来源不稳定。
         */
        private const val MYAPP_SEARCH_GUID = "ef61398e-6c91-4d6a-87f4-ab2674156614"
    }
}

/** F-Droid /api/v1/packages/{pkg} 返回结构（仅取所需字段） */
private data class FdroidPackageDto(
    val packageName: String? = null,
    val name: Map<String, String>? = null,
    val summary: Map<String, String>? = null,
    val authorName: String? = null,
    val webSite: String? = null,
    val icon: String? = null,
    val categories: List<String>? = null,
    val versions: List<FdroidVersionDto>? = null
)

private data class FdroidVersionDto(
    val versionName: String? = null,
    val versionCode: Long? = null,
    val added: Long? = null,
    val apkName: String? = null,
    val size: Long? = null,
    val repo: FdroidRepoDto? = null
)

private data class FdroidRepoDto(
    val address: String? = null,
    val type: String? = null
)

/** F-Droid 官方搜索 API 返回的候选项（字段兼容字符串或 {locale:text} 对象，并兼容常见别名） */
private data class FdroidSearchItemDto(
    @SerializedName("packageName", alternate = ["package_name", "id"]) val packageName: String? = null,
    @SerializedName("name", alternate = ["title", "appName"]) val name: Any? = null,
    @SerializedName("summary", alternate = ["description", "short_description"]) val summary: Any? = null,
    @SerializedName("icon", alternate = ["iconPath", "icon_path", "iconUrl", "icon_url"]) val icon: String? = null,
    @SerializedName("version", alternate = ["versionName", "latestVersion", "version_name"]) val version: String? = null,
    @SerializedName("author", alternate = ["authorName"]) val author: String? = null,
    @SerializedName("categories", alternate = ["category"]) val categories: List<String>? = null
)

/** 搜索上游元信息（供管理端 UI 列出所有源） */
data class SearchProviderMeta(
    val id: String,
    val name: String,
    val searchUrl: String,
    val enabled: Boolean
)

/** IzzyOnDroid index-v1.json 顶层结构（仅取所需字段） */
private data class IzzyIndexDto(
    val apps: List<IzzyAppDto> = emptyList(),
    val packages: Map<String, List<IzzyPkgDto>>? = null,
    val repo: IzzyRepoDto? = null
)

/** index-v1.json 的 apps[] 条目 */
private data class IzzyAppDto(
    @SerializedName("packageName", alternate = ["id"]) val packageName: String? = null,
    val name: Map<String, String>? = null,
    val summary: Map<String, String>? = null,
    val icon: String? = null,
    val categories: List<String>? = null,
    val authorName: String? = null,
    val webSite: String? = null
)

/** index-v1.json 的 packages[pkg][] 条目 */
private data class IzzyPkgDto(
    val versionCode: Long? = null,
    val versionName: String? = null,
    val apkName: String? = null,
    val size: Long? = null
)

/** index-v1.json 的 repo 对象 */
private data class IzzyRepoDto(
    val url: String? = null
)
