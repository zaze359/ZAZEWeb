package com.zaze.server.feature.appmarket.repository

import com.zaze.server.database.repository.BaseRepository
import com.zaze.server.feature.appmarket.pojo.App
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AppRepository : BaseRepository<App, Long> {
    /** 按包名查找应用（采集时用于幂等 upsert） */
    fun findByPackageName(packageName: String): App?

    /**
     * 按关键词搜索：名称 / 包名 / 开发者 / 分类 / 简介，忽略大小写。
     */
    @Query("""
        SELECT a FROM App a WHERE
        LOWER(a.name) LIKE LOWER(CONCAT('%', :kw, '%')) OR
        LOWER(a.packageName) LIKE LOWER(CONCAT('%', :kw, '%')) OR
        LOWER(a.developer) LIKE LOWER(CONCAT('%', :kw, '%')) OR
        LOWER(a.category) LIKE LOWER(CONCAT('%', :kw, '%')) OR
        LOWER(a.summary) LIKE LOWER(CONCAT('%', :kw, '%'))
    """)
    fun search(@Param("kw") keyword: String): List<App>
}
