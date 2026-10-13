package com.zaze.server.feature.appmarket.incremental

import com.zaze.server.feature.appmarket.incremental.exception.BsdiffException
import com.zaze.server.feature.appmarket.incremental.util.Sha256
import io.sigpipe.jbsdiff.Diff
import io.sigpipe.jbsdiff.Patch
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * bsdiff 算法封装（jbsdiff / malensek，Apache-2.0，Maven Central `io.sigpipe:jbsdiff:1.0`）。
 *
 * - 默认压缩为 **bzip2**，生成的 patch 可被标准 C 版 `bspatch` 还原（见 prd R8）。
 * - [Diff.diff] / [Patch.patch] 会把整个文件读入 `byte[]`；因此调用方**必须**在外部做体积护栏
 *   （见 [com.zaze.server.feature.appmarket.incremental.service.IncrementalUpdateService]）。
 * - 所有方法都是纯文件操作，不依赖 Spring 上下文，便于单测。
 */
@Component
class BsdiffService {

    /** 生成差分包：old + new → patch。 */
    fun diff(old: Path, new: Path, patch: Path) {
        try {
            val oldBytes = Files.readAllBytes(old)
            val newBytes = Files.readAllBytes(new)
            ByteArrayOutputStream().use { baos ->
                Diff.diff(oldBytes, newBytes, baos)
                Files.write(patch, baos.toByteArray())
            }
        } catch (e: Exception) {
            throw BsdiffException("bsdiff 生成差分包失败：${e.message}", e)
        }
    }

    /** 合并还原：old + patch → out（还原出的新包）。 */
    fun patch(old: Path, patch: Path, out: Path) {
        try {
            val oldBytes = Files.readAllBytes(old)
            val patchBytes = Files.readAllBytes(patch)
            ByteArrayOutputStream().use { baos ->
                Patch.patch(oldBytes, patchBytes, baos)
                Files.write(out, baos.toByteArray())
            }
        } catch (e: Exception) {
            throw BsdiffException("bspatch 合并还原失败：${e.message}", e)
        }
    }

    /**
     * 服务端自校验（prd R3 核心门槛）：diff 后用同一 old + patch 还原，
     * 断言还原包与原始 new 的 SHA-256 一致。不一致说明该 patch 不可信，不应下发。
     */
    fun verify(old: Path, patch: Path, expectedNew: Path): Boolean {
        val expectedSha = Sha256.of(expectedNew)
        val recon = Files.createTempFile("bsdiff-recon-", ".apk")
        try {
            patch(old, patch, recon)
            return Sha256.of(recon) == expectedSha
        } finally {
            Files.deleteIfExists(recon)
        }
    }

    /** 差分包首部是否为标准 `BSDIFF40`（未来 C 客户端兼容前提）。 */
    fun isStandardBsdiff(patch: Path): Boolean {
        if (!Files.exists(patch) || Files.size(patch) < 8) return false
        val head = Files.readAllBytes(patch).copyOfRange(0, 8)
        return head.contentEquals(BSDIFF40_MAGIC.toByteArray())
    }

    companion object {
        private const val BSDIFF40_MAGIC = "BSDIFF40"
    }
}
