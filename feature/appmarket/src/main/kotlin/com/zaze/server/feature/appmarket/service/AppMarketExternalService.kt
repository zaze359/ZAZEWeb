package com.zaze.server.feature.appmarket.service

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.zaze.server.common.utils.JsonUtil
import com.zaze.server.feature.appmarket.collector.AppMarketCollector
import com.zaze.server.feature.appmarket.dto.ExternalAppPreview
import com.zaze.server.feature.appmarket.model.asVo
import com.zaze.server.feature.appmarket.pojo.App
import com.zaze.server.feature.appmarket.pojo.AppVersion
import com.zaze.server.feature.appmarket.pojo.DownloadSource
import com.zaze.server.feature.appmarket.repository.AppRepository
import com.zaze.server.feature.appmarket.repository.AppVersionRepository
import com.zaze.server.feature.appmarket.repository.DownloadSourceRepository
import com.zaze.server.feature.appmarket.vo.AppVo
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.util.*

/**
 * 外部应用查询与一键导入（当前上游：F-Droid）。
 *
 * - [lookup]：按包名（或商店/官网 URL，自动提取包名）查询 F-Droid 元数据，返回预览；
 *   上游不可达或包名不存在时返回 null（由控制器转成友好的空结果）。
 * - [importApp]：一键把 F-Droid 的「应用 + 最新版本 + 下载源」写入三张表，并自动补全应用宝（MyApp）商店源。
 *   已存在同包名应用时抛 [IllegalArgumentException]，避免重复导入。
 */
@Service
@CacheConfig(cacheNames = ["appmarket"])
class AppMarketExternalService(
    private val okHttpClient: OkHttpClient,
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

    /** 按包名/链接精确查询预览（不写库） */
    fun lookup(raw: String): ExternalAppPreview? {
        val pkgName = normalizePackageName(raw) ?: return null
        val pkg = fetchFdroid(pkgName) ?: return null
        return toPreview(pkg)
    }

    /**
     * 按应用名模糊搜索，返回候选列表（不写库）。
     * - 优先调用 F-Droid 官方搜索 API（服务端按 q 过滤）；
     * - 解析失败时兼容「数组 / {results:[...]} 包裹 / 单对象」多种返回结构；
     * - 客户端再做一次关键词命中过滤作为兜底；
     * - 上游不可达或接口异常时抛 [IllegalStateException]，由控制器转成友好提示（而非静默返回空）。
     */
    fun searchByName(keyword: String): List<ExternalAppPreview> {
        val kw = keyword.trim()
        if (kw.isEmpty()) return emptyList()
        val items = fetchSearch(kw)
            ?: throw IllegalStateException("暂无法连接 F-Droid 搜索服务（网络不可达或接口异常），请稍后重试")
        val lower = kw.lowercase()
        return items.asSequence()
            .mapNotNull { toSearchPreview(it) }
            .filter { matchKeyword(it, lower) }
            .take(MAX_SEARCH_RESULTS)
            .toList()
    }

    /** 候选是否命中关键词（名称 / 简介 / 包名，忽略大小写） */
    private fun matchKeyword(p: ExternalAppPreview, lower: String): Boolean {
        return listOf(p.name, p.summary, p.packageName)
            .any { it != null && it.toString().lowercase().contains(lower) }
    }

    /**
     * 一键导入：F-Droid 应用 + 最新版本 + 下载源，并自动补应用宝源。
     * 命中缓存需在导入后清除，确保门户/后台列表立即可见。
     */
    @Transactional
    @CacheEvict(allEntries = true)
    fun importApp(raw: String): AppVo {
        val pkgName = normalizePackageName(raw)
            ?: throw IllegalArgumentException("无法从输入中识别包名：$raw")
        if (appRepository.findByPackageName(pkgName) != null) {
            throw IllegalArgumentException("包名 $pkgName 已存在，无需重复导入")
        }
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
            sourceUrl = "https://f-droid.org/packages/$pkgName"
        )
    }

    private fun fetchFdroid(packageName: String): FdroidPackageDto? {
        val url = fdroidBase + packageName
        // 外部网络可能偶发抖动，最多重试一次
        repeat(2) {
            val dto = doFetch(url)
            if (dto != null) return dto
        }
        return null
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
            repeat(2) {
                val items = doFetchSearch(url)
                if (items != null) {
                    synchronized(searchCache) { searchCache[cacheKey] = now to items }
                    return items
                }
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
            okHttpClient.newCall(request).execute().use { resp ->
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
            latestVersionName = item.version
        )
    }

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
            okHttpClient.newCall(request).execute().use { resp ->
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

    companion object {
        private val PACKAGE_RE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+\$")
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
