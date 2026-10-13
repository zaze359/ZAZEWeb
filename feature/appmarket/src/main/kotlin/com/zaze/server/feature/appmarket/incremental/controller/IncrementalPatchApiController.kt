package com.zaze.server.feature.appmarket.incremental.controller

import com.zaze.server.common.controller.BaseController
import com.zaze.server.common.controller.Response
import com.zaze.server.feature.appmarket.incremental.exception.PatchUnavailableException
import com.zaze.server.feature.appmarket.incremental.service.IncrementalUpdateService
import org.springframework.core.io.UrlResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files

/**
 * 增量更新 - 下发契约接口（供未来学生端 App 调用）。
 * 挂载在 `/api/v1/appmarket`，沿用既有登录鉴权（与 [com.zaze.server.feature.appmarket.controller.AppMarketApiController] 同待遇），零改动。
 *
 * 成功：`200` + 差分包字节流 + 头 `X-Old-Sha256` / `X-New-Sha256` / `X-Patch-Size`（供客户端校验）。
 * 不可用（体积超阈 / 上游不可达 / 自校验失败 / 超时）：`200` + JSON 信封（`data=null`），引导客户端回退完整 APK 下载，
 * 恒 200、非 500（prd R6 / AC5）。
 *
 * 客户端消费约定（仓库外实现）：校验已装旧包 SHA-256 == `X-Old-Sha256` → 下载 patch → 用标准 `bspatch` 还原 →
 * 校验还原包 SHA-256 == `X-New-Sha256` → 安装。
 */
@RestController
@RequestMapping("/api/v1/appmarket")
class IncrementalPatchApiController(
    private val svc: IncrementalUpdateService
) : BaseController() {

    @GetMapping("/incremental-patch")
    fun fetch(
        @RequestParam appId: Long,
        @RequestParam fromVersionId: Long,
        @RequestParam toVersionId: Long
    ): ResponseEntity<*> {
        return try {
            val dl = svc.fetchPatch(appId, fromVersionId, toVersionId)
            val file = dl.file
            val meta = dl.meta
            val resource = UrlResource(file.toUri())
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${meta.key}.patch\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Old-Sha256", meta.oldSha256)
                .header("X-New-Sha256", meta.newSha256)
                .header("X-Patch-Size", meta.patchSize.toString())
                .contentLength(Files.size(file))
                .body(resource)
        } catch (e: PatchUnavailableException) {
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Response<Any?>(200, null, e.message ?: "请下载完整 APK"))
        } catch (e: Exception) {
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Response<Any?>(200, null, e.message ?: "增量更新暂不可用，请下载完整 APK"))
        }
    }
}
