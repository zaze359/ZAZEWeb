package com.zaze.server.feature.appmarket.controller

import com.zaze.server.common.aop.LoggerManage
import com.zaze.server.common.controller.BaseController
import com.zaze.server.common.controller.Response
import com.zaze.server.feature.appmarket.dto.AppFormDto
import com.zaze.server.feature.appmarket.dto.CollectResultVo
import com.zaze.server.feature.appmarket.dto.ExternalAppPreview
import com.zaze.server.feature.appmarket.dto.SourceFormDto
import com.zaze.server.feature.appmarket.dto.SyncStoreResultVo
import com.zaze.server.feature.appmarket.dto.VersionFormDto
import com.zaze.server.feature.appmarket.service.AppMarketAdminService
import com.zaze.server.feature.appmarket.service.AppMarketExternalService
import com.zaze.server.feature.appmarket.service.SearchProviderMeta
import com.zaze.server.feature.appmarket.vo.AppDetailVo
import com.zaze.server.feature.appmarket.vo.AppVersionVo
import com.zaze.server.feature.appmarket.vo.AppVo
import com.zaze.server.feature.appmarket.vo.DownloadSourceVo
import org.springframework.web.bind.annotation.*

/**
 * 应用市场 - 管理端接口（后台页面调用）。
 * 统一返回 [Response] 信封，与门户接口 / 既有控制器一致。
 */
@RestController
@RequestMapping("/api/v1/appmarket/admin")
class AppMarketAdminApiController(
    private val adminService: AppMarketAdminService,
    private val externalService: AppMarketExternalService
) : BaseController() {

    @PostMapping("/collect")
    @LoggerManage(description = "触发应用市场自动采集")
    fun collect(): Response<CollectResultVo> {
        return Response(adminService.collect())
    }

    @PostMapping("/sync-store-sources")
    @LoggerManage(description = "同步第三方商店源（应用宝）")
    fun syncStoreSources(): Response<SyncStoreResultVo> {
        return Response(adminService.syncStoreSources())
    }

    @GetMapping("/apps")
    @LoggerManage(description = "管理端-应用列表")
    fun listApps(): Response<List<AppVo>> {
        return Response(adminService.listApps())
    }

    @GetMapping("/apps/{id}")
    @LoggerManage(description = "管理端-应用详情（含版本与下载源）")
    fun getAppDetail(@PathVariable id: Long): Response<AppDetailVo?> {
        return Response(adminService.getAppDetail(id))
    }

    @PostMapping("/apps")
    @LoggerManage(description = "管理端-新增应用")
    fun createApp(@RequestBody form: AppFormDto): Response<AppVo> {
        return Response(adminService.createApp(form))
    }

    @PutMapping("/apps/{id}")
    @LoggerManage(description = "管理端-更新应用")
    fun updateApp(@PathVariable id: Long, @RequestBody form: AppFormDto): Response<AppVo?> {
        return Response(adminService.updateApp(id, form))
    }

    @DeleteMapping("/apps/{id}")
    @LoggerManage(description = "管理端-删除应用（级联版本与下载源）")
    fun deleteApp(@PathVariable id: Long): Response<Boolean> {
        return Response(adminService.deleteApp(id))
    }

    @PostMapping("/apps/{appId}/versions")
    @LoggerManage(description = "管理端-新增版本")
    fun addVersion(@PathVariable appId: Long, @RequestBody form: VersionFormDto): Response<AppVersionVo?> {
        return Response(adminService.addVersion(appId, form))
    }

    @DeleteMapping("/versions/{id}")
    @LoggerManage(description = "管理端-删除版本（级联下载源）")
    fun deleteVersion(@PathVariable id: Long): Response<Boolean> {
        return Response(adminService.deleteVersion(id))
    }

    @PostMapping("/versions/{versionId}/sources")
    @LoggerManage(description = "管理端-新增下载源")
    fun addSource(@PathVariable versionId: Long, @RequestBody form: SourceFormDto): Response<DownloadSourceVo?> {
        return Response(adminService.addSource(versionId, form))
    }

    @DeleteMapping("/sources/{id}")
    @LoggerManage(description = "管理端-删除下载源")
    fun deleteSource(@PathVariable id: Long): Response<Boolean> {
        return Response(adminService.deleteSource(id))
    }

    @GetMapping("/external-lookup")
    @LoggerManage(description = "管理端-查询外部应用(F-Droid)")
    fun lookupExternal(@RequestParam packageName: String): Response<ExternalAppPreview?> {
        val preview = externalService.lookup(packageName)
        return if (preview != null) Response(preview)
        else Response(200, null, "未找到该包名对应的外部应用（F-Droid 无记录，或网络不可达）")
    }

    @GetMapping("/external-search")
    @LoggerManage(description = "管理端-按应用名搜索外部应用(跨上游)")
    fun searchExternal(@RequestParam keyword: String): Response<List<ExternalAppPreview>> {
        return try {
            Response(externalService.searchByName(keyword))
        } catch (e: IllegalStateException) {
            // 上游不可达 / 接口异常：返回明确提示，而非静默空列表（避免前台误显「请求成功」）
            Response(200, emptyList(), e.message ?: "搜索服务暂不可用，请稍后重试")
        }
    }

    @GetMapping("/external-sources")
    @LoggerManage(description = "管理端-列出外部搜索源")
    fun listExternalSources(): Response<List<SearchProviderMeta>> {
        return Response(externalService.listSearchProviders())
    }

    @PostMapping("/external-import")
    @LoggerManage(description = "管理端-一键导入外部应用(跨上游)")
    fun importExternal(
        @RequestParam packageName: String,
        @RequestParam(required = false) source: String?
    ): Response<AppVo?> {
        return try {
            Response(externalService.importApp(packageName, source))
        } catch (e: IllegalArgumentException) {
            Response(200, null, e.message ?: "导入失败")
        }
    }
}
