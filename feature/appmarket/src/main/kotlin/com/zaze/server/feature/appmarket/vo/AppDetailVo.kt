package com.zaze.server.feature.appmarket.vo

import java.io.Serializable

/**
 * 应用完整详情：基础信息 + 版本列表（每版本含下载源）
 */
data class AppDetailVo(
    val id: Long,
    val name: String,
    val packageName: String?,
    val category: String?,
    val developer: String?,
    val summary: String?,
    val iconUrl: String?,
    val officialUrl: String?,
    val versions: List<AppVersionVo> = emptyList()
) : Serializable
