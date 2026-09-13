package com.zaze.server.feature.appmarket.vo

import java.io.Serializable

/**
 * 版本 DTO（含该版本下的下载源列表）
 */
data class AppVersionVo(
    val id: Long,
    val appId: Long,
    val versionName: String?,
    val versionCode: Long?,
    val releaseDate: Long?,
    val sizeMb: Long?,
    val changelog: String?,
    val sourceCount: Int = 0,
    val sources: List<DownloadSourceVo> = emptyList()
) : Serializable
