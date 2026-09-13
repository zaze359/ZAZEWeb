package com.zaze.server.feature.appmarket.repository

import com.zaze.server.database.repository.BaseRepository
import com.zaze.server.feature.appmarket.pojo.DownloadSource
import org.springframework.data.repository.query.Param

interface DownloadSourceRepository : BaseRepository<DownloadSource, Long> {
    fun findByVersionId(@Param("versionId") versionId: Long): List<DownloadSource>

    /** 采集时按 versionId + 下载地址判重 */
    fun findByVersionIdAndDownloadUrl(@Param("versionId") versionId: Long, @Param("downloadUrl") downloadUrl: String): DownloadSource?
}
