package com.zaze.server.feature.appmarket.service

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

    /** 查询预览（不写库） */
    fun lookup(raw: String): ExternalAppPreview? {
        val pkgName = normalizePackageName(raw) ?: return null
        val pkg = fetchFdroid(pkgName) ?: return null
        return toPreview(pkg)
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
            iconDataUri = pkg.icon,
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
