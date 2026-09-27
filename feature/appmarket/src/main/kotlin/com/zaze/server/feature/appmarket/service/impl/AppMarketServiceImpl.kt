package com.zaze.server.feature.appmarket.service.impl

import com.zaze.server.feature.appmarket.dto.PageResult
import com.zaze.server.feature.appmarket.model.asVo
import com.zaze.server.feature.appmarket.pojo.App
import com.zaze.server.feature.appmarket.repository.AppRepository
import com.zaze.server.feature.appmarket.repository.AppVersionRepository
import com.zaze.server.feature.appmarket.repository.DownloadSourceRepository
import com.zaze.server.feature.appmarket.service.AppMarketService
import com.zaze.server.feature.appmarket.vo.AppDetailVo
import com.zaze.server.feature.appmarket.vo.AppVo
import com.zaze.server.feature.appmarket.vo.CategoryCountVo
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

    override fun searchApps(keyword: String): List<AppVo> {
        val kw = keyword.trim()
        if (kw.isBlank()) return listApps()
        return appRepository.search(kw).map { app ->
            app.asVo(versionRepository.countByAppId(app.id).toInt())
        }
    }

    override fun listAppsPaged(
        page: Int,
        size: Int,
        keyword: String?,
        category: String?,
        sort: String?
    ): PageResult<AppVo> {
        // 1. 按关键词 / 分类过滤出基础集合（全量数据规模小，过滤在内存做即可）
        val base: List<App> = when {
            !keyword.isNullOrBlank() -> appRepository.search(keyword)
            !category.isNullOrBlank() && category != "全部" -> appRepository.findByCategory(category)
            else -> appRepository.findAll()
        }
        // 2. 排序：latest 按创建时间倒序；name 按名称；hot 需版本数，映射后再排
        val sortedBase = when (sort) {
            "name" -> base.sortedBy { it.name ?: "" }
            "hot" -> base
            else -> base.sortedByDescending { it.createTime }
        }
        val vos = sortedBase.map { it.asVo(versionRepository.countByAppId(it.id).toInt()) }
        val sorted = if (sort == "hot") vos.sortedByDescending { it.versionCount } else vos
        // 3. 切片分页
        val total = sorted.size
        val from = ((page - 1).coerceAtLeast(0)) * size
        val to = minOf(from + size, total)
        val slice = if (from < total) sorted.subList(from, to) else emptyList()
        return PageResult(list = slice, total = total, page = page, size = size)
    }

    override fun listCategoryCounts(): List<CategoryCountVo> {
        val all = appRepository.findAll()
        return all.groupBy { it.category ?: "未分类" }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .map { CategoryCountVo(it.key, it.value) }
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
