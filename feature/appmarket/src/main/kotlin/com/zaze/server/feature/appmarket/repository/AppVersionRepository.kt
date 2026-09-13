package com.zaze.server.feature.appmarket.repository

import com.zaze.server.database.repository.BaseRepository
import com.zaze.server.feature.appmarket.pojo.AppVersion
import org.springframework.data.repository.query.Param

interface AppVersionRepository : BaseRepository<AppVersion, Long> {
    fun findByAppId(@Param("appId") appId: Long): List<AppVersion>
    fun countByAppId(@Param("appId") appId: Long): Long

    /** 采集时按 appId + 版本名判重 */
    fun findByAppIdAndVersionName(@Param("appId") appId: Long, @Param("versionName") versionName: String): AppVersion?
}
