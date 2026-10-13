package com.zaze.server.feature.appmarket.incremental.service

import com.zaze.server.feature.appmarket.incremental.BsdiffService
import com.zaze.server.feature.appmarket.incremental.ApkCacheStore
import com.zaze.server.feature.appmarket.incremental.PatchCacheStore
import com.zaze.server.feature.appmarket.incremental.config.IncrementalProperties
import com.zaze.server.feature.appmarket.incremental.dto.PatchMetaVo
import com.zaze.server.feature.appmarket.incremental.dto.PatchListVo
import com.zaze.server.feature.appmarket.incremental.dto.PatchSidecar
import com.zaze.server.feature.appmarket.incremental.dto.VerifyVo
import com.zaze.server.feature.appmarket.incremental.exception.ApkFetchException
import com.zaze.server.feature.appmarket.incremental.exception.BsdiffException
import com.zaze.server.feature.appmarket.incremental.exception.PatchUnavailableException
import com.zaze.server.feature.appmarket.incremental.util.Sha256
import com.zaze.server.feature.appmarket.pojo.AppVersion
import com.zaze.server.feature.appmarket.repository.AppVersionRepository
import com.zaze.server.feature.appmarket.repository.DownloadSourceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PreDestroy

/**
 * 增量更新编排服务。串联：版本/下载源解析 → APK 拉取缓存 → 体积护栏 → 差分包生成（限时）→
 * 服务端自校验 → 文件缓存；对外暴露 `diff` / `fetchPatch` / `verify` / `listPatches`。
 *
 * 设计要点（详见 design.md）：
 * - 体积护栏：old/new 任一直观体积（version.sizeMb 预判 + 实际文件大小复核）超 `maxApkMb` 即降级。
 * - 超时护栏：diff 在独立单线程 Executor 中执行，[java.util.concurrent.Future.get] 限时，超时即降级，不挂起请求线程。
 * - 自校验门槛：每次生成的 patch 必须 [BsdiffService.verify] 通过（还原包 SHA == 原始新包 SHA），
 *   否则不缓存、不下发，直接抛 [BsdiffException] → 由控制器转降级信封。
 * - 缓存：同一 `(oldVersionId, newVersionId)` 只算一次，命中直接返回 `cached=true`。
 */
@Service
class IncrementalUpdateService(
    private val versionRepository: AppVersionRepository,
    private val sourceRepository: DownloadSourceRepository,
    private val apkCache: ApkCacheStore,
    private val patchCache: PatchCacheStore,
    private val bsdiff: BsdiffService,
    private val props: IncrementalProperties
) {
    private val log = LoggerFactory.getLogger(IncrementalUpdateService::class.java)

    private val diffExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "bsdiff-diff").also { it.isDaemon = true }
    }

    private val mb: Long get() = 1024L * 1024L

    // ---------------------------------------------------------------- 对外 API

    /** 生成（或返回已缓存）差分包元数据。appId 可选，提供时校验版本归属。 */
    fun diff(oldVersionId: Long, newVersionId: Long, appId: Long? = null): PatchMetaVo {
        val pair = resolvePair(oldVersionId, newVersionId, appId)
        // 体积护栏（版本元数据预判）
        if ((pair.oldVer.sizeMb ?: 0) > props.maxApkMb ||
            (pair.newVer.sizeMb ?: 0) > props.maxApkMb
        ) {
            throw PatchUnavailableException("APK 超过体积上限 ${props.maxApkMb}MB，建议下载完整 APK")
        }
        // 体积护栏（实际文件大小复核）
        if (pair.oldSize > props.maxApkMb * mb || pair.newSize > props.maxApkMb * mb) {
            throw PatchUnavailableException("APK 超过体积上限 ${props.maxApkMb}MB，建议下载完整 APK")
        }

        val key = "$oldVersionId-$newVersionId"
        val cached = patchCache.readSidecar(key)
        if (cached != null) {
            return cached.toMeta(key, cached = true)
        }

        val future = diffExecutor.submit<PatchSidecar> {
            val patch = Files.createTempFile("bsdiff-", ".patch")
            try {
                bsdiff.diff(pair.oldFile, pair.newFile, patch)
                // 服务端自校验：还原后必须与原始新包字节一致
                if (!bsdiff.verify(pair.oldFile, patch, pair.newFile)) {
                    throw BsdiffException("自校验失败：还原包 SHA 与原始新包不一致（old=$oldVersionId new=$newVersionId）")
                }
                val newSha = Sha256.of(pair.newFile)
                val oldSha = Sha256.of(pair.oldFile)
                val patchBytes = Files.readAllBytes(patch)
                PatchSidecar(
                    oldVersionId = oldVersionId,
                    newVersionId = newVersionId,
                    oldSha256 = oldSha,
                    newSha256 = newSha,
                    oldSize = pair.oldSize,
                    newSize = pair.newSize,
                    patchSize = patchBytes.size.toLong(),
                    ratio = ratio(patchBytes.size.toLong(), pair.newSize),
                    createdAt = System.currentTimeMillis()
                ).also { sidecar ->
                    patchCache.write(key, patchBytes, sidecar)
                }
            } finally {
                Files.deleteIfExists(patch)
            }
        }

        return try {
            val sidecar = future.get(props.diffTimeoutSec, TimeUnit.SECONDS)
            sidecar.toMeta(key, cached = false)
        } catch (e: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            throw PatchUnavailableException("diff 超时（>${props.diffTimeoutSec}s），建议下载完整 APK")
        }
    }

    /** 下发契约：按需生成/命中缓存，返回 patch 文件 + 元数据（供控制器带 X-* 头流式返回）。 */
    fun fetchPatch(appId: Long, fromVersionId: Long, toVersionId: Long): PatchDownload {
        val meta = diff(fromVersionId, toVersionId, appId)
        val file = patchCache.resolve(meta.key)
            ?: throw PatchUnavailableException("差分包未生成，请稍后重试或下载完整 APK")
        return PatchDownload(file, meta)
    }

    /** 触发自校验（管理端可观测）。 */
    fun verify(oldVersionId: Long, newVersionId: Long): VerifyVo {
        val pair = resolvePair(oldVersionId, newVersionId, null)
        val patch = Files.createTempFile("bsdiff-", ".patch")
        try {
            bsdiff.diff(pair.oldFile, pair.newFile, patch)
            val recon = Files.createTempFile("bsdiff-recon-", ".apk")
            try {
                bsdiff.patch(pair.oldFile, patch, recon)
                return VerifyVo(
                    pass = Sha256.of(recon) == Sha256.of(pair.newFile),
                    oldSha256 = Sha256.of(pair.oldFile),
                    newSha256 = Sha256.of(pair.newFile),
                    reconSha256 = Sha256.of(recon),
                    patchSize = Files.size(patch)
                )
            } finally {
                Files.deleteIfExists(recon)
            }
        } finally {
            Files.deleteIfExists(patch)
        }
    }

    fun listPatches(): List<PatchListVo> = patchCache.list()

    /** 供下载接口取已缓存 patch 文件；不存在返回 null。 */
    fun resolvePatchFile(key: String): Path? = patchCache.resolve(key)

    // ---------------------------------------------------------------- 内部

    private fun resolvePair(oldVersionId: Long, newVersionId: Long, appId: Long?): Resolved {
        val oldVer = versionRepository.findById(oldVersionId).orElse(null)
            ?: throw PatchUnavailableException("旧版本不存在：$oldVersionId")
        val newVer = versionRepository.findById(newVersionId).orElse(null)
            ?: throw PatchUnavailableException("新版本不存在：$newVersionId")
        if (appId != null && (oldVer.appId != appId || newVer.appId != appId)) {
            throw PatchUnavailableException("版本 $oldVersionId / $newVersionId 不属于应用 $appId")
        }
        val oldFile = apkCache.resolve(firstDownloadUrl(oldVer.id))
        val newFile = apkCache.resolve(firstDownloadUrl(newVer.id))
        return Resolved(oldVer, newVer, oldFile, newFile, Files.size(oldFile), Files.size(newFile))
    }

    private fun firstDownloadUrl(versionId: Long): String {
        val sources = sourceRepository.findByVersionId(versionId)
        val url = sources.firstNotNullOfOrNull { it.downloadUrl?.takeIf { u -> u.isNotBlank() } }
            ?: throw PatchUnavailableException("版本 $versionId 无可用下载源")
        return url
    }

    private fun ratio(patchSize: Long, newSize: Long): Double =
        if (newSize == 0L) 0.0 else patchSize.toDouble() / newSize

    private data class Resolved(
        val oldVer: AppVersion,
        val newVer: AppVersion,
        val oldFile: Path,
        val newFile: Path,
        val oldSize: Long,
        val newSize: Long
    )

    data class PatchDownload(val file: Path, val meta: PatchMetaVo)

    @PreDestroy
    fun shutdown() {
        diffExecutor.shutdownNow()
    }
}
