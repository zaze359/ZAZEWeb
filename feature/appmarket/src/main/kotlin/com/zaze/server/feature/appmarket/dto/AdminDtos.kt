package com.zaze.server.feature.appmarket.dto

import com.zaze.server.feature.appmarket.vo.AppVo

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
 * 第三方商店源同步结果统计（应用宝）
 */
data class SyncStoreResultVo(
    val appsProcessed: Int = 0,
    val sourcesAdded: Int = 0
)

/**
 * 外部应用（F-Droid）查询预览。
 * iconDataUri 为 F-Droid 返回的 base64 图标（data:image/...;base64,...），仅在后台预览展示，不持久化。
 */
data class ExternalAppPreview(
    val packageName: String? = null,
    val name: String? = null,
    val summary: String? = null,
    val iconSrc: String? = null,
    val developer: String? = null,
    val officialUrl: String? = null,
    val category: String? = null,
    val latestVersionName: String? = null,
    val latestVersionCode: Long? = null,
    val sizeMb: Long? = null,
    val apkUrl: String? = null,
    val sourceUrl: String? = null,
    /** 结果来自哪个搜索上游（F-Droid / IzzyOnDroid），用于前台按源展示与导入溯源 */
    val source: String? = null
)

/**
 * 管理后台 - 从 APK 导入请求。
 *
 * 由前端用 app-info-parser 解析**本地** APK 后提交：APK 二进制不上传服务端，
 * 此处只接收解析出的元数据（受 CORS 限制，前端无法直接解析远端 APK）。
 * [iconDataUri] 为 APK 内图标的 base64（data:image/...;base64,...），会持久化到 App.iconUrl。
 */
data class ApkImportRequest(
    val packageName: String? = null,
    val name: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val iconDataUri: String? = null,
    val sizeMb: Long? = null
)

/**
 * 从 APK 导入的结果。
 *
 * @param app 导入后的应用
 * @param appCreated 本次是否新建了应用（false 表示复用了库中已有应用）
 * @param versionAdded 本次是否新增了版本（false 表示该版本已存在，无需重复导入）
 * @param sourcesAdded 本次新增的下载源数量（应用宝详情页，已存在则跳过）
 */
data class ApkImportResultVo(
    val app: AppVo,
    val appCreated: Boolean = false,
    val versionAdded: Boolean = false,
    val sourcesAdded: Int = 0
)
