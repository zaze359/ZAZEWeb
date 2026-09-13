package com.zaze.server.feature.appmarket.vo

import java.io.Serializable

/**
 * 下载源 DTO
 */
data class DownloadSourceVo(
    val id: Long,
    val versionId: Long,
    val sourceName: String?,
    val sourceType: String?,
    val downloadUrl: String?,
    val region: String?,
    val note: String?
) : Serializable
