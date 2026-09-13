package com.zaze.server.feature.appmarket.dto

/**
 * 管理后台 - 应用表单
 */
data class AppFormDto(
    val name: String? = null,
    val packageName: String? = null,
    val category: String? = null,
    val developer: String? = null,
    val summary: String? = null,
    val iconUrl: String? = null,
    val officialUrl: String? = null
)

/**
 * 管理后台 - 版本表单
 */
data class VersionFormDto(
    val versionName: String? = null,
    val versionCode: Long? = null,
    /** yyyy-MM-dd */
    val releaseDate: String? = null,
    val sizeMb: Long? = null,
    val changelog: String? = null
)

/**
 * 管理后台 - 下载源表单
 */
data class SourceFormDto(
    val sourceName: String? = null,
    val sourceType: String? = null,
    val downloadUrl: String? = null,
    val region: String? = null,
    val note: String? = null
)

/**
 * 自动采集结果统计
 */
data class CollectResultVo(
    val targets: Int = 0,
    val appsCreated: Int = 0,
    val versionsAdded: Int = 0,
    val sourcesAdded: Int = 0,
    val success: Boolean = true,
    val messages: List<String> = emptyList()
)

/**
 * 第三方商店源同步结果统计（酷安 / 应用宝）
 */
data class SyncStoreResultVo(
    val appsProcessed: Int = 0,
    val sourcesAdded: Int = 0
)
