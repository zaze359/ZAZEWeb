package com.zaze.server.feature.appmarket.service.impl

import com.zaze.server.feature.appmarket.model.asVo
import com.zaze.server.feature.appmarket.pojo.App
import com.zaze.server.feature.appmarket.repository.AppRepository
import com.zaze.server.feature.appmarket.repository.AppVersionRepository
import com.zaze.server.feature.appmarket.repository.DownloadSourceRepository
import com.zaze.server.feature.appmarket.service.AppMarketService
import com.zaze.server.feature.appmarket.vo.AppDetailVo
import com.zaze.server.feature.appmarket.vo.AppVo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
@CacheConfig(cacheNames = ["appmarket"])
class AppMarketServiceImpl : AppMarketService {

    @Autowired
    private lateinit var appRepository: AppRepository

    @Autowired
    private lateinit var versionRepository: AppVersionRepository

    @Autowired
    private lateinit var sourceRepository: DownloadSourceRepository

    @Cacheable
    override fun listApps(): List<AppVo> {
        return appRepository.findAll().map { app ->
            app.asVo(versionRepository.countByAppId(app.id).toInt())
        }
    }

    @Cacheable
    override fun getAppDetail(appId: Long): AppDetailVo? {
        val app: App = appRepository.findById(appId).orElse(null) ?: return null
        val versions = versionRepository.findByAppId(appId).map { version ->
            val sources = sourceRepository.findByVersionId(version.id).map { it.asVo() }
            version.asVo(sources)
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
}
