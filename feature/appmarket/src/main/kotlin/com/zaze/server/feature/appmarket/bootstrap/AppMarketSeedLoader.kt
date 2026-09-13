package com.zaze.server.feature.appmarket.bootstrap

import com.zaze.server.common.ext.jsonToList
import com.zaze.server.common.utils.FileUtil
import com.zaze.server.feature.appmarket.pojo.App
import com.zaze.server.feature.appmarket.pojo.AppVersion
import com.zaze.server.feature.appmarket.pojo.DownloadSource
import com.zaze.server.feature.appmarket.repository.AppRepository
import com.zaze.server.feature.appmarket.repository.AppVersionRepository
import com.zaze.server.feature.appmarket.repository.DownloadSourceRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.util.*

/**
 * 启动期种子数据加载。
 *
 * 仅在 `appmarket_app` 表为空时执行：从 classpath 读取已整理的公开下载地址 JSON 并入库。
 * 这是「收集网上的」第一阶段的落地方式——人工整理真实公开来源 URL 入库，不做自有存储/二进制托管。
 *
 * 未来扩展点：将本类替换为 `AppMarketCollector`，借助 core/network 的 OkHttp 拉取真实页面/接口
 * 自动抓取并更新三张表；数据访问层（Repository）与上层 Service / VO 无需变动。
 */
@Component
class AppMarketSeedLoader(
    private val appRepository: AppRepository,
    private val versionRepository: AppVersionRepository,
    private val sourceRepository: DownloadSourceRepository
) : CommandLineRunner {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")

    override fun run(vararg args: String?) {
        if (appRepository.count() > 0) return
        val resource = ClassPathResource("data/appmarket_seed.json")
        if (!resource.exists()) return
        val json = FileUtil.readByBytes(resource.inputStream).toString()
        val seeds = json.jsonToList(AppSeedDto::class.java) ?: return
        for (seed in seeds) {
            val app = appRepository.save(
                App(
                    name = seed.name,
                    packageName = seed.packageName,
                    category = seed.category,
                    developer = seed.developer,
                    summary = seed.summary,
                    iconUrl = seed.iconUrl,
                    officialUrl = seed.officialUrl
                )
            )
            for (v in seed.versions.orEmpty()) {
                val version = versionRepository.save(
                    AppVersion(
                        appId = app.id,
                        versionName = v.versionName,
                        versionCode = v.versionCode,
                        releaseDate = parseDate(v.releaseDate),
                        sizeMb = v.sizeMb,
                        changelog = v.changelog
                    )
                )
                for (s in v.sources.orEmpty()) {
                    sourceRepository.save(
                        DownloadSource(
                            versionId = version.id,
                            sourceName = s.sourceName,
                            sourceType = s.sourceType,
                            downloadUrl = s.downloadUrl,
                            region = s.region,
                            note = s.note
                        )
                    )
                }
            }
        }
    }

    private fun parseDate(s: String?): Date? {
        if (s.isNullOrBlank()) return null
        return try {
            dateFormat.parse(s)
        } catch (e: Exception) {
            null
        }
    }
}

data class AppSeedDto(
    val name: String? = null,
    val packageName: String? = null,
    val category: String? = null,
    val developer: String? = null,
    val summary: String? = null,
    val iconUrl: String? = null,
    val officialUrl: String? = null,
    val versions: List<VersionSeedDto>? = null
)

data class VersionSeedDto(
    val versionName: String? = null,
    val versionCode: Long? = null,
    val releaseDate: String? = null,
    val sizeMb: Long? = null,
    val changelog: String? = null,
    val sources: List<SourceSeedDto>? = null
)

data class SourceSeedDto(
    val sourceName: String? = null,
    val sourceType: String? = null,
    val downloadUrl: String? = null,
    val region: String? = null,
    val note: String? = null
)
