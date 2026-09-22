package com.zaze.server.feature.appmarket.controller

import com.zaze.server.common.aop.LoggerManage
import com.zaze.server.common.controller.BaseController
import com.zaze.server.common.controller.Response
import com.zaze.server.feature.appmarket.dto.ApkImportRequest
import com.zaze.server.feature.appmarket.dto.ApkImportResultVo
import com.zaze.server.feature.appmarket.dto.AppFormDto
import com.zaze.server.feature.appmarket.dto.CollectResultVo
import com.zaze.server.feature.appmarket.dto.ExternalAppPreview
import com.zaze.server.feature.appmarket.dto.ExternalLookupVo
import com.zaze.server.feature.appmarket.dto.ExternalSearchVo
import com.zaze.server.feature.appmarket.dto.ImportTaskRef
import com.zaze.server.feature.appmarket.dto.ImportTaskRequest
import com.zaze.server.feature.appmarket.dto.ImportTaskSnapshot
import com.zaze.server.feature.appmarket.dto.SourceFormDto
import com.zaze.server.feature.appmarket.dto.BatchCompleteResultVo
import com.zaze.server.feature.appmarket.dto.VersionFormDto
import com.zaze.server.feature.appmarket.service.AppMarketAdminService
import com.zaze.server.feature.appmarket.service.AppMarketExternalService
import com.zaze.server.feature.appmarket.service.ImportProgressService
import com.zaze.server.feature.appmarket.service.SearchProviderMeta
import com.zaze.server.feature.appmarket.vo.AppDetailVo
import com.zaze.server.feature.appmarket.vo.AppVersionVo
import com.zaze.server.feature.appmarket.vo.AppVo
import com.zaze.server.feature.appmarket.vo.DownloadSourceVo
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * 应用市场 - 管理端接口（后台页面调用）。
 * 统一返回 [Response] 信封，与门户接口 / 既有控制器一致。
 */
@RestController
@RequestMapping("/api/v1/appmarket/admin")
class AppMarketAdminApiController(
    private val adminService: AppMarketAdminService,
    private val externalService: AppMarketExternalService,
    private val importProgress: ImportProgressService
) : BaseController() {

    @PostMapping("/collect")
    @LoggerManage(description = "触发应用市场自动采集")
    fun collect(): Response<CollectResultVo> {
        return Response(adminService.collect())
    }

    @PostMapping("/batch-complete-myapp")
    @LoggerManage(description = "批量补全应用宝元数据")
    fun batchCompleteFromMyApp(): Response<BatchCompleteResultVo> {
        return Response(adminService.batchCompleteFromMyApp())
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

    /**
     * 按包名精确查询。返回体**区分开**两部分（不混在一起）：
     * - [ExternalLookupVo.preview]：最终采用的预览（按上游优先级取首个命中）
     * - [ExternalLookupVo.probes]：每个上游**各自**的状态与耗时（命中/未命中/超时/异常）
     */
    @GetMapping("/external-lookup")
    @LoggerManage(description = "管理端-查询外部应用(跨上游)")
    fun lookupExternal(@RequestParam packageName: String): Response<ExternalLookupVo> {
        val vo = externalService.lookup(packageName)
        return Response(200, vo, if (vo.preview != null) "请求成功" else "未找到该包名；详见 probes 中各上游的状态")
    }

    /**
     * 按应用名模糊搜索。返回体同样**区分开**两部分：
     * - [ExternalSearchVo.items]：聚合去重后的候选列表
     * - [ExternalSearchVo.probes]：每个上游**各自**的状态与耗时（本地词典/F-Droid/Izzy/APKPure/Aptoide/Bing发现）
     */
    @GetMapping("/external-search")
    @LoggerManage(description = "管理端-按应用名搜索外部应用(跨上游)")
    fun searchExternal(@RequestParam keyword: String): Response<ExternalSearchVo> {
        val vo = externalService.searchByName(keyword)
        return Response(200, vo, if (vo.items.isNotEmpty()) "请求成功" else "无匹配结果；详见 probes 中各上游的状态")
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

    /**
     * 从 APK 解析结果导入：前端用 app-info-parser 解析**本地** APK 后仅提交元数据，
     * APK 二进制不上传服务端。
     *
     * 注意：本接口**不加** [@LoggerManage]——该切面会序列化入参进日志，
     * 而入参含体积较大的 base64 图标，会撑爆日志。
     */
    @PostMapping("/import-from-apk")
    fun importFromApk(@RequestBody form: ApkImportRequest): Response<ApkImportResultVo?> {
        return try {
            val result = adminService.importFromApk(form)
            val msg = when {
                result.appCreated -> "已导入：${result.app.name}"
                result.versionAdded -> "已新增版本：${result.app.name}"
                else -> "已是最新，无需重复导入"
            }
            Response(200, result, msg)
        } catch (e: IllegalArgumentException) {
            Response(200, null, e.message ?: "导入失败")
        }
    }

    // ------------------------------------------------------------ 导入链路实时追踪（SSE）
    /**
     * 启动一个「可追踪」的导入任务：立即返回 taskId，导入在后台线程执行，避免前端长时间同步等待。
     * 前端随后用 [streamImportTask] 订阅 SSE 流，实时接收每一步链路明细
     * （请求了哪个上游 / URL / 耗时 / 命中与否 / 写了哪些表）。
     *
     * [ImportTaskRequest.kind]：
     * - `external-import`：一键导入（需 packageName，可选 source）
     * - `batch-complete`：批量补全应用宝元数据（无需参数）
     */
    @PostMapping("/import-tasks")
    @LoggerManage(description = "管理端-启动导入任务(带链路追踪)")
    fun startImportTask(@RequestBody form: ImportTaskRequest): Response<ImportTaskRef?> {
        return when (form.kind) {
            "external-import" -> {
                val pkg = form.packageName?.trim()
                if (pkg.isNullOrBlank()) return Response(200, null, "缺少 packageName")
                val src = form.source?.trim()?.takeIf { it.isNotEmpty() }
                val title = "导入 $pkg${if (src != null) "（来源：$src）" else ""}"
                val id = importProgress.start("external-import", title) {
                    val vo = externalService.importApp(pkg, src)
                    "已导入：${vo.name}（包名 ${vo.packageName}）"
                }
                Response(ImportTaskRef(id, "external-import", title))
            }
            "batch-complete" -> {
                val title = "批量补全应用宝元数据"
                val id = importProgress.start("batch-complete", title) {
                    val r = externalService.batchCompleteFromMyApp()
                    "共处理 ${r.appsProcessed} 个 → 更新 ${r.appsUpdated} / 跳过 ${r.appsSkipped} / 失败 ${r.appsFailed}"
                }
                Response(ImportTaskRef(id, "batch-complete", title))
            }
            else -> Response(200, null, "未知任务类型：${form.kind}")
        }
    }

    /**
     * 订阅导入任务的实时链路流（SSE）。
     * 后订阅的客户端会先收到一份 `snapshot` 回放，再增量收 `step`；任务已结束则直接回放 + `end` 并关闭流，
     * 因此「任务跑了一半才订阅」也不会丢步骤。
     */
    @GetMapping("/import-tasks/{taskId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamImportTask(@PathVariable taskId: String): SseEmitter {
        return importProgress.subscribe(taskId)
    }

    /** 任务快照（SSE 之外的兜底：一次性拉取当前进度） */
    @GetMapping("/import-tasks/{taskId}")
    fun getImportTask(@PathVariable taskId: String): Response<ImportTaskSnapshot?> {
        val snap = importProgress.snapshot(taskId)
        return if (snap != null) Response(snap) else Response(200, null, "任务不存在或已过期")
    }
}
