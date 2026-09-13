package com.zaze.server.feature.appmarket.collector

import com.google.gson.annotations.SerializedName
import com.zaze.server.common.ext.jsonToList
import com.zaze.server.common.utils.FileUtil
import com.zaze.server.feature.appmarket.dto.CollectResultVo
import com.zaze.server.feature.appmarket.dto.SyncStoreResultVo
import com.zaze.server.feature.appmarket.pojo.App
import com.zaze.server.feature.appmarket.pojo.AppVersion
import com.zaze.server.feature.appmarket.pojo.DownloadSource
import com.zaze.server.feature.appmarket.repository.AppRepository
import com.zaze.server.feature.appmarket.repository.AppVersionRepository
import com.zaze.server.feature.appmarket.repository.DownloadSourceRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 应用市场 - 自动采集器。
 *
 * 读取 classpath `data/appmarket_collect_targets.json` 中配置的采集目标，通过 OkHttp 访问公开上游
 * （当前实现 GitHub Releases API），把真实版本与下载地址幂等写入三张表：
 * 已存在的应用/版本/下载源不会重复插入，仅补充新增项。
 *
 * 设计为「可手动触发」（管理后台按钮）而非定时任务，避免无节制抓取上游。
 * 扩展点：新增 provider（如 FDROID / APKMIRROR）时，在 [collectForTarget] 的 when 分支补充即可，
 * 数据访问层与上层 Service / VO 无需改动。
 *
 * 第三方应用商店（应用宝）的详情页 URL 可由 packageName 确定性推导，无需联网抓取，
 * 因此统一在 [ensureStoreSources] 中按 version 幂等补源；[collect] 与 [syncStoreSources] 都会复用它。
 */
@Service
class AppMarketCollector(
    private val okHttpClient: OkHttpClient,
    private val appRepository: AppRepository,
    private val versionRepository: AppVersionRepository,
    private val sourceRepository: DownloadSourceRepository
) {

    private val log = LoggerFactory.getLogger(AppMarketCollector::class.java)

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun collect(): CollectResultVo {
        val targets = loadTargets()
        var appsCreated = 0
        var versionsAdded = 0
        var sourcesAdded = 0
        val messages = mutableListOf<String>()

        for (target in targets) {
            try {
                when (target.provider?.uppercase(Locale.US)) {
                    "GITHUB" -> {
                        val stat = collectGithub(target)
                        appsCreated += stat.appsCreated
                        versionsAdded += stat.versionsAdded
                        sourcesAdded += stat.sourcesAdded
                        messages.add(
                            "GitHub ${target.repo}: 新增 ${stat.versionsAdded} 个版本 / ${stat.sourcesAdded} 个下载源"
                        )
                    }
                    else -> messages.add("跳过 ${target.repo}：暂不支持的来源 ${target.provider}")
                }
            } catch (e: Exception) {
                log.warn("采集失败 target={}", target.repo, e)
                messages.add("采集 ${target.repo} 失败：${e.message}")
            }
        }
        return CollectResultVo(
            targets = targets.size,
            appsCreated = appsCreated,
            versionsAdded = versionsAdded,
            sourcesAdded = sourcesAdded,
            success = true,
            messages = messages
        )
    }

    private data class GithubStat(val appsCreated: Int, val versionsAdded: Int, val sourcesAdded: Int)

    private fun collectGithub(target: CollectTargetDto): GithubStat {
        val repo = target.repo?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("采集目标 repo 为空")
        val limit = target.releaseLimit ?: DEFAULT_RELEASE_LIMIT

        var appsCreated = 0
        val packageName = target.packageName?.takeIf { it.isNotBlank() } ?: repo.replace('/', '.')
        val app = appRepository.findByPackageName(packageName) ?: run {
            appsCreated++
            appRepository.save(
                App(
                    name = target.appName ?: repo,
                    packageName = packageName,
                    category = target.category,
                    developer = target.developer,
                    summary = target.summary,
                    iconUrl = target.iconUrl,
                    officialUrl = target.officialUrl
                )
            )
        }

        var versionsAdded = 0
        var sourcesAdded = 0
        try {
            val url = "https://api.github.com/repos/$repo/releases?per_page=$limit"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "zaze-appmarket-collector")
                .get()
                .build()
            val body = okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("GitHub API 返回 HTTP ${resp.code}")
                }
                resp.body?.string() ?: ""
            }
            val releases = body.jsonToList(GitHubReleaseDto::class.java).orEmpty()
            for (release in releases) {
                if (release.draft == true) continue
                val versionName = normalizeVersion(release.tagName) ?: continue
                val version = versionRepository.findByAppIdAndVersionName(app.id, versionName) ?: run {
                    versionsAdded++
                    versionRepository.save(
                        AppVersion(
                            appId = app.id,
                            versionName = versionName,
                            versionCode = parseVersionCode(versionName),
                            releaseDate = parseIsoDate(release.publishedAt),
                            sizeMb = assetSizeMb(release),
                            changelog = release.body?.take(CHANGELOG_MAX_LEN)
                        )
                    )
                }
                for (asset in release.assets.orEmpty()) {
                    val downloadUrl = asset.browserDownloadUrl?.takeIf { it.isNotBlank() } ?: continue
                    if (sourceRepository.findByVersionIdAndDownloadUrl(version.id, downloadUrl) != null) continue
                    sourcesAdded++
                    sourceRepository.save(
                        DownloadSource(
                            versionId = version.id,
                            sourceName = asset.name ?: "GitHub Release",
                            sourceType = "GITHUB",
                            downloadUrl = downloadUrl,
                            region = "",
                            note = "自动采集自 GitHub Releases"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            log.warn("GitHub 抓取失败 target={}，商店源仍会补全", target.repo, e)
        }
        // 同步第三方商店详情页源（应用宝），随采集一并补全（与 GitHub 是否成功无关）
        sourcesAdded += ensureStoreSources(app)
        return GithubStat(appsCreated, versionsAdded, sourcesAdded)
    }

    /**
     * 为某个应用的所有版本幂等补上第三方商店详情页下载源（应用宝）。
     * URL 由 packageName 确定性推导，无需联网；已存在则跳过，避免重复插入。
     * 返回本次新增的源数量。
     */
    fun ensureStoreSources(app: App): Int {
        var added = 0
        for (version in versionRepository.findByAppId(app.id)) {
            for (spec in STORE_SPECS) {
                val url = spec.prefix + app.packageName
                if (sourceRepository.findByVersionIdAndDownloadUrl(version.id, url) != null) continue
                sourceRepository.save(
                    DownloadSource(
                        versionId = version.id,
                        sourceName = spec.name,
                        sourceType = spec.type,
                        downloadUrl = url,
                        region = "中国",
                        note = "第三方应用商店详情页（自动同步）"
                    )
                )
                added++
            }
        }
        return added
    }

    /**
     * 全量同步所有应用的第三方商店源（应用宝）。
     * 覆盖采集器不处理的纯种子应用（如国内主流 app），可由管理后台按需触发。
     */
    fun syncStoreSources(): SyncStoreResultVo {
        var appsProcessed = 0
        var sourcesAdded = 0
        for (app in appRepository.findAll()) {
            appsProcessed++
            sourcesAdded += ensureStoreSources(app)
        }
        return SyncStoreResultVo(appsProcessed = appsProcessed, sourcesAdded = sourcesAdded)
    }

    // ---------------------------------------------------------------- helpers

    private fun loadTargets(): List<CollectTargetDto> {
        val resource = ClassPathResource(TARGETS_FILE)
        if (!resource.exists()) return emptyList()
        val json = FileUtil.readByBytes(resource.inputStream).toString()
        return json.jsonToList(CollectTargetDto::class.java).orEmpty()
    }

    /** 版本 tag 归一：去掉前缀 v，例：v0.29.1 -> 0.29.1 */
    private fun normalizeVersion(tag: String?): String? {
        val t = tag?.trim().orEmpty()
        if (t.isEmpty()) return null
        return t.removePrefix("v").removePrefix("V")
    }

    private fun parseVersionCode(version: String): Long? {
        val digits = version.filter { it.isDigit() }
        return digits.toLongOrNull()
    }

    private fun parseIsoDate(iso: String?): Date? {
        if (iso.isNullOrBlank()) return null
        return try {
            isoFormat.parse(iso)
        } catch (e: Exception) {
            null
        }
    }

    /** 取首个 asset 体积换算为 MB（用于展示） */
    private fun assetSizeMb(release: GitHubReleaseDto): Long? {
        val size = release.assets.orEmpty().mapNotNull { it.size }.firstOrNull() ?: return null
        return size / 1024 / 1024
    }

    companion object {
        private const val TARGETS_FILE = "data/appmarket_collect_targets.json"
        private const val DEFAULT_RELEASE_LIMIT = 3
        private const val CHANGELOG_MAX_LEN = 2000

        /** 第三方应用商店详情页源：由 packageName 推导 URL，随采集/全量同步自动补全（酷安已停止应用市场分发，仅保留应用宝） */
        private val STORE_SPECS = listOf(
            StoreSourceSpec("MYAPP", "应用宝", "https://sj.qq.com/appdetail/")
        )
    }

    /** 第三方商店源规格：type=sourceType，name=展示名，prefix=详情页 URL 前缀（后缀为 packageName） */
    private data class StoreSourceSpec(val type: String, val name: String, val prefix: String)
}

/** 采集目标配置项 */
data class CollectTargetDto(
    val provider: String? = null,
    val repo: String? = null,
    val appName: String? = null,
    val packageName: String? = null,
    val category: String? = null,
    val developer: String? = null,
    val summary: String? = null,
    val iconUrl: String? = null,
    val officialUrl: String? = null,
    val releaseLimit: Int? = null
)

/** GitHub Releases API 返回项（仅取所需字段） */
data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String? = null,
    val name: String? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    val body: String? = null,
    val draft: Boolean? = null,
    val prerelease: Boolean? = null,
    val assets: List<GitHubAssetDto>? = null
)

/** GitHub Release 资产（下载文件） */
data class GitHubAssetDto(
    val name: String? = null,
    @SerializedName("browser_download_url") val browserDownloadUrl: String? = null,
    val size: Long? = null
)
