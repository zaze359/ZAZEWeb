package com.zaze.server.feature.appmarket.service

import com.zaze.server.feature.appmarket.dto.ApkImportRequest
import com.zaze.server.feature.appmarket.dto.ApkImportResultVo
import com.zaze.server.feature.appmarket.dto.AppFormDto
import com.zaze.server.feature.appmarket.dto.CollectResultVo
import com.zaze.server.feature.appmarket.dto.SourceFormDto
import com.zaze.server.feature.appmarket.dto.SyncStoreResultVo
import com.zaze.server.feature.appmarket.dto.VersionFormDto
import com.zaze.server.feature.appmarket.vo.AppDetailVo
import com.zaze.server.feature.appmarket.vo.AppVersionVo
import com.zaze.server.feature.appmarket.vo.AppVo
import com.zaze.server.feature.appmarket.vo.DownloadSourceVo

/**
 * 应用市场 - 管理端服务（后台页面的读写入口）。
 *
 * 与面向门户的只读 [AppMarketService] 分离：本接口包含写操作（增删改）与自动采集触发。
 */
interface AppMarketAdminService {

    fun listApps(): List<AppVo>

    fun getAppDetail(appId: Long): AppDetailVo?

    fun createApp(form: AppFormDto): AppVo

    fun updateApp(id: Long, form: AppFormDto): AppVo?

    /** 删除应用，并级联删除其下所有版本与下载源 */
    fun deleteApp(id: Long): Boolean

    fun addVersion(appId: Long, form: VersionFormDto): AppVersionVo?

    /** 删除版本，并级联删除其下所有下载源 */
    fun deleteVersion(versionId: Long): Boolean

    fun addSource(versionId: Long, form: SourceFormDto): DownloadSourceVo?

    fun deleteSource(sourceId: Long): Boolean

    /** 触发一次自动采集（从公开上游抓取真实版本与下载地址） */
    fun collect(): CollectResultVo

    /** 全量同步第三方商店源（应用宝），按 packageName 为所有应用的版本补全详情页下载源 */
    fun syncStoreSources(): SyncStoreResultVo

    /**
     * 从 APK 解析结果导入：前端解析**本地** APK 后只提交元数据，APK 二进制不上传服务端。
     *
     * - 应用不存在则创建；已存在则在其下追加新版本（版本号相同则跳过，视为「已是最新」）；
     * - 始终按 packageName 幂等补全应用宝详情页下载源。
     *
     * 包名缺失时抛 [IllegalArgumentException]（包名是判重与推导下载源的前提）。
     */
    fun importFromApk(form: ApkImportRequest): ApkImportResultVo
}
