package com.zaze.server.feature.appmarket.incremental.controller

import com.zaze.server.common.aop.LoggerManage
import com.zaze.server.common.controller.BaseController
import com.zaze.server.common.controller.Response
import com.zaze.server.feature.appmarket.incremental.dto.PatchListVo
import com.zaze.server.feature.appmarket.incremental.dto.PatchMetaVo
import com.zaze.server.feature.appmarket.incremental.dto.VerifyVo
import com.zaze.server.feature.appmarket.incremental.exception.ApkFetchException
import com.zaze.server.feature.appmarket.incremental.exception.BsdiffException
import com.zaze.server.feature.appmarket.incremental.exception.PatchUnavailableException
import com.zaze.server.feature.appmarket.incremental.service.IncrementalUpdateService
import org.springframework.core.io.UrlResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.file.Files

/**
 * 增量更新 - 管理端接口（后台页面调用）。
 * 挂载在 `/api/v1/appmarket/admin/incremental-update`，沿用管理端（/admin/ 前缀）的 ADMIN 鉴权，零改动。
 *
 * 生成/校验/列表接口统一返回 [Response] 信封；下载接口返回字节流。
 * 所有「不可用」异常（体积超阈 / 上游不可达 / 自校验失败 / 超时）统一降级为 `Response(200, null, reason)`，
 * 绝不透传为 500（prd R6 / AC5）。
 */
@RestController
@RequestMapping("/api/v1/appmarket/admin/incremental-update")
class IncrementalUpdateAdminController(
    private val svc: IncrementalUpdateService
) : BaseController() {

    @PostMapping("/diff")
    @LoggerManage(description = "管理端-生成 APK 差分包")
    fun diff(
        @RequestParam oldVersionId: Long,
        @RequestParam newVersionId: Long
    ): Response<PatchMetaVo> {
        return try {
            Response(svc.diff(oldVersionId, newVersionId))
        } catch (e: PatchUnavailableException) {
            Response(200, null, e.message ?: "暂不支持增量更新，请下载完整 APK")
        } catch (e: BsdiffException) {
            Response(200, null, e.message ?: "差分包生成失败")
        } catch (e: ApkFetchException) {
            Response(200, null, e.message ?: "APK 拉取失败")
        }
    }

    /**
     * 下载已缓存的差分包字节流。不加 [@LoggerManage]：返回的是二进制流，无结构化入参可记。
     */
    @GetMapping("/patches/{key}/download")
    fun download(@PathVariable key: String): ResponseEntity<*> {
        val file = svc.resolvePatchFile(key) ?: return ResponseEntity.notFound().build<Any>()
        val resource = UrlResource(file.toUri())
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$key.patch\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(Files.size(file))
            .body(resource)
    }

    @PostMapping("/verify")
    @LoggerManage(description = "管理端-校验差分包自洽性")
    fun verify(
        @RequestParam oldVersionId: Long,
        @RequestParam newVersionId: Long
    ): Response<VerifyVo> {
        return try {
            Response(svc.verify(oldVersionId, newVersionId))
        } catch (e: Exception) {
            Response(200, null, e.message ?: "校验失败")
        }
    }

    @GetMapping("/patches")
    @LoggerManage(description = "管理端-列出已缓存差分包")
    fun list(): Response<List<PatchListVo>> = Response(svc.listPatches())
}
