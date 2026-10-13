package com.zaze.server.feature.appmarket.incremental

import com.google.gson.Gson
import com.zaze.server.feature.appmarket.incremental.config.IncrementalProperties
import com.zaze.server.feature.appmarket.incremental.dto.PatchListVo
import com.zaze.server.feature.appmarket.incremental.dto.PatchSidecar
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * 差分包文件系统缓存（prd R4 / R10）：不引入 JPA 实体，patch 与 sidecar json 落盘，
 * 满足「零 DB schema 改动」。key 即 `<oldVersionId>-<newVersionId>`。
 */
@Component
class PatchCacheStore(private val props: IncrementalProperties) {

    private val gson = Gson()

    fun exists(key: String): Boolean {
        val patch = patchFile(key)
        val sidecar = sidecarFile(key)
        return Files.exists(patch) && Files.exists(sidecar) && sidecarValid(sidecar)
    }

    /** 命中缓存时返回 patch 文件；不存在返回 null。 */
    fun resolve(key: String): Path? {
        val patch = patchFile(key)
        return if (Files.exists(patch)) patch else null
    }

    fun readSidecar(key: String): PatchSidecar? {
        val sidecar = sidecarFile(key)
        if (!sidecarValid(sidecar)) return null
        return try {
            gson.fromJson(Files.readString(sidecar), PatchSidecar::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun write(key: String, patchBytes: ByteArray, sidecar: PatchSidecar) {
        val dir = Path.of(props.patchDir)
        Files.createDirectories(dir)
        Files.write(patchFile(key), patchBytes)
        Files.write(sidecarFile(key), gson.toJson(sidecar).toByteArray())
    }

    fun list(): List<PatchListVo> {
        val dir = Path.of(props.patchDir)
        if (!Files.isDirectory(dir)) return emptyList()
        val files = Files.list(dir).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".patch") }
                .collect(Collectors.toList())
        }
        return files.mapNotNull { patch ->
            val key = patch.fileName.toString().removeSuffix(".patch")
            readSidecar(key)?.let { sc ->
                PatchListVo(
                    key = key,
                    ratio = sc.ratio,
                    patchSize = sc.patchSize,
                    newSize = sc.newSize,
                    createdAt = sc.createdAt
                )
            }
        }
    }

    private fun patchFile(key: String): Path =
        Path.of(props.patchDir).resolve("$key.patch")

    private fun sidecarFile(key: String): Path =
        Path.of(props.patchDir).resolve("$key.json")

    /** sidecar 损坏（大小异常）视为未命中，强制重新生成。 */
    private fun sidecarValid(sidecar: Path): Boolean {
        return try {
            Files.size(sidecar) in 1..(64 * 1024)
        } catch (e: Exception) {
            false
        }
    }
}
