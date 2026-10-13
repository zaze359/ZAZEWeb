package com.zaze.server.feature.appmarket.incremental

import com.zaze.server.feature.appmarket.incremental.util.Sha256
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * BsdiffService 自校验测试（不依赖网络 / 真实 APK）：
 * 用两段高共性二进制（1MB 随机字节 + 100KB 不同的随机尾巴）验证
 * 1) diff→patch 还原后与原始 new SHA-256 逐字节一致（prd AC1 核心门槛）；
 * 2) patch 首部为标准 BSDIFF40（未来 C 客户端兼容前提，prd R8）；
 * 3) 差分包体积显著小于完整新包（prd AC2 增量有效性）。
 *
 * 真实 APK 的端到端闭环（H2 冒烟）见 implement.md §P7。
 */
class BsdiffServiceTest {

    private val bsdiff = BsdiffService()

    @Test
    fun `diff then patch reconstructs identical new file (round-trip)`() {
        val dir = Files.createTempDirectory("bsdiff-test")
        val old = dir.resolve("old.bin")
        val new = dir.resolve("new.bin")

        // 高相似但字节多样的二进制：1MB 随机前缀 + 100KB 不同的随机尾巴。
        // 注意：不能用全同字节（如全 'A'），否则会触发 bsdiff 后缀排序的最坏情况 O(n²) 退化而几乎卡死。
        val rand = java.util.Random(42L)
        val oldBytes = ByteArray(1024 * 1024) { rand.nextInt(256).toByte() }
        Files.write(old, oldBytes)
        val newBytes = oldBytes.copyOf(oldBytes.size + 100 * 1024)
        val tail = ByteArray(100 * 1024) { rand.nextInt(256).toByte() }
        tail.copyInto(newBytes, oldBytes.size)
        Files.write(new, newBytes)

        val patch = dir.resolve("patch.bin")
        bsdiff.diff(old, new, patch)
        val recon = dir.resolve("recon.bin")
        bsdiff.patch(old, patch, recon)

        assertEquals(Sha256.of(new), Sha256.of(recon), "还原包必须与原始新包 SHA-256 一致")
        assertTrue(bsdiff.isStandardBsdiff(patch), "patch 首部应为标准 BSDIFF40")

        val ratio = Files.size(patch).toDouble() / Files.size(new)
        assertTrue(ratio < 1.0, "增量差分包应小于完整新包，实际 ratio=$ratio")
        println(
            "bsdiff round-trip OK: new=${Files.size(new)} patch=${Files.size(patch)} " +
                "ratio=${"%.3f".format(ratio)}"
        )
    }
}
