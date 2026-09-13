package com.zaze.server.feature.appmarket.service.impl

import com.zaze.server.feature.appmarket.collector.AppMarketCollector
import com.zaze.server.feature.appmarket.dto.AppFormDto
import com.zaze.server.feature.appmarket.dto.CollectResultVo
import com.zaze.server.feature.appmarket.dto.SourceFormDto
import com.zaze.server.feature.appmarket.dto.SyncStoreResultVo
import com.zaze.server.feature.appmarket.dto.VersionFormDto
import com.zaze.server.feature.appmarket.model.asVo
import com.zaze.server.feature.appmarket.pojo.App
import com.zaze.server.feature.appmarket.pojo.AppVersion
import com.zaze.server.feature.appmarket.pojo.DownloadSource
import com.zaze.server.feature.appmarket.repository.AppRepository
import com.zaze.server.feature.appmarket.repository.AppVersionRepository
import com.zaze.server.feature.appmarket.repository.DownloadSourceRepository
import com.zaze.server.feature.appmarket.service.AppMarketAdminService
import com.zaze.server.feature.appmarket.vo.AppDetailVo
import com.zaze.server.feature.appmarket.vo.AppVersionVo
import com.zaze.server.feature.appmarket.vo.AppVo
import com.zaze.server.feature.appmarket.vo.DownloadSourceVo
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat

@Service
@CacheConfig(cacheNames = ["appmarket"])
class AppMarketAdminServiceImpl(
    private val appRepository: AppRepository,
    private val versionRepository: AppVersionRepository,
    private val sourceRepository: DownloadSourceRepository,
    private val collector: AppMarketCollector
) : AppMarketAdminService {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")

    override fun listApps(): List<AppVo> {
        return appRepository.findAll().map { app ->
            app.asVo(versionRepository.countByAppId(app.id).toInt())
        }
    }

    override fun getAppDetail(appId: Long): AppDetailVo? {
        val app = appRepository.findById(appId).orElse(null) ?: return null
        val versions = versionRepository.findByAppId(appId).map { version ->
            version.asVo(sourceRepository.findByVersionId(version.id).map { it.asVo() })
        }
        return AppDetailVo(
            id = app.id,
            name = app.name ?: "",
            packageName = app.packageName,
            category = app.category,
            developer = app.developer,
            summary = app.summary,
            iconUrl = app.iconUrl,
            officialUrl = app.officialUrl,
            versions = versions
        )
    }

    @Transactional
    @CacheEvict(allEntries = true)
    override fun createApp(form: AppFormDto): AppVo {
        val app = appRepository.save(
            App(
                name = form.name,
                packageName = form.packageName,
                category = form.category,
                developer = form.developer,
                summary = form.summary,
                iconUrl = form.iconUrl,
                officialUrl = form.officialUrl
            )
        )
        return app.asVo(0)
    }

    @Transactional
    @CacheEvict(allEntries = true)
    override fun updateApp(id: Long, form: AppFormDto): AppVo? {
        val existing = appRepository.findById(id).orElse(null) ?: return null
        val updated = existing.copy(
            name = form.name ?: existing.name,
            packageName = form.packageName ?: existing.packageName,
            category = form.category ?: existing.category,
            developer = form.developer ?: existing.developer,
            summary = form.summary ?: existing.summary,
            iconUrl = form.iconUrl ?: existing.iconUrl,
            officialUrl = form.officialUrl ?: existing.officialUrl
        )
        val saved = appRepository.save(updated)
        return saved.asVo(versionRepository.countByAppId(saved.id).toInt())
    }

    @Transactional
    @CacheEvict(allEntries = true)
    override fun deleteApp(id: Long): Boolean {
        if (!appRepository.existsById(id)) return false
        for (version in versionRepository.findByAppId(id)) {
            sourceRepository.deleteAll(sourceRepository.findByVersionId(version.id))
        }
        versionRepository.deleteAll(versionRepository.findByAppId(id))
        appRepository.deleteById(id)
        return true
    }

    @Transactional
    @CacheEvict(allEntries = true)
    override fun addVersion(appId: Long, form: VersionFormDto): AppVersionVo? {
        if (!appRepository.existsById(appId)) return null
        val version = versionRepository.save(
            AppVersion(
                appId = appId,
                versionName = form.versionName,
                versionCode = form.versionCode,
                releaseDate = parseDate(form.releaseDate),
                sizeMb = form.sizeMb,
                changelog = form.changelog
            )
        )
        return version.asVo()
    }

    @Transactional
    @CacheEvict(allEntries = true)
    override fun deleteVersion(versionId: Long): Boolean {
        if (!versionRepository.existsById(versionId)) return false
        sourceRepository.deleteAll(sourceRepository.findByVersionId(versionId))
        versionRepository.deleteById(versionId)
        return true
    }

    @Transactional
    @CacheEvict(allEntries = true)
    override fun addSource(versionId: Long, form: SourceFormDto): DownloadSourceVo? {
        if (!versionRepository.existsById(versionId)) return null
        val source = sourceRepository.save(
            DownloadSource(
                versionId = versionId,
                sourceName = form.sourceName,
                sourceType = form.sourceType,
                downloadUrl = form.downloadUrl,
                region = form.region,
                note = form.note
            )
        )
        return source.asVo()
    }

    @Transactional
    @CacheEvict(allEntries = true)
    override fun deleteSource(sourceId: Long): Boolean {
        if (!sourceRepository.existsById(sourceId)) return false
        sourceRepository.deleteById(sourceId)
        return true
    }

    @CacheEvict(allEntries = true)
    override fun collect(): CollectResultVo {
        return collector.collect()
    }

    @CacheEvict(allEntries = true)
    override fun syncStoreSources(): SyncStoreResultVo {
        return collector.syncStoreSources()
    }

    private fun parseDate(s: String?): java.util.Date? {
        if (s.isNullOrBlank()) return null
        return try {
            dateFormat.parse(s)
        } catch (e: Exception) {
            null
        }
    }
}
