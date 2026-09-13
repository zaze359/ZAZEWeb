package com.zaze.server.feature.appmarket.vo

import java.io.Serializable

/**
 * 应用列表/详情用 DTO
 */
data class AppVo(
    val id: Long,
    val name: String,
    val packageName: String?,
    val category: String?,
    val developer: String?,
    val summary: String?,
    val iconUrl: String?,
    val officialUrl: String?,
    val versionCount: Int = 0
) : Serializable
