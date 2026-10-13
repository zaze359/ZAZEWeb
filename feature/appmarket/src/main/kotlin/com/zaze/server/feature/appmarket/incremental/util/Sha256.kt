package com.zaze.server.feature.appmarket.incremental.util

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** 流式 SHA-256 工具（不整包读内存，按块更新 digest）。 */
object Sha256 {
    fun of(file: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buf = ByteArray(8192)
            var n = input.read(buf)
            while (n != -1) {
                md.update(buf, 0, n)
                n = input.read(buf)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun of(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
