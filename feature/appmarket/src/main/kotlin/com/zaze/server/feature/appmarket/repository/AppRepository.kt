package com.zaze.server.feature.appmarket.repository

import com.zaze.server.database.repository.BaseRepository
import com.zaze.server.feature.appmarket.pojo.App

interface AppRepository : BaseRepository<App, Long> {
    /** 按包名查找应用（采集时用于幂等 upsert） */
    fun findByPackageName(packageName: String): App?
}
