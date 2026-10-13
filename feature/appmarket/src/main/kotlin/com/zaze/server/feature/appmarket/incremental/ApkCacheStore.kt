package com.zaze.server.feature.appmarket.incremental

import com.zaze.server.feature.appmarket.incremental.config.IncrementalProperties
import com.zaze.server.feature.appmarket.incremental.exception.ApkFetchException
import com.zaze.server.feature.appmarket.incremental.util.Sha256
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * APK 字节缓存：按 `downloadUrl` 从下载源拉取 APK 到本地临时缓存（带 TTL），
 * 供 diff 使用。复用 collector 同款 `OkHttpClient`（含系统代理），并把读超时放宽到 5 分钟
 * 以容忍大包下载。
 *
 * 同 key 并发去重：多个请求同时拉同一 URL 时只真正下载一次。
 */
@Component
class ApkCacheStore(
    okHttpClient: OkHttpClient,
    private val props: IncrementalProperties
) {
    private val log = LoggerFactory.getLogger(ApkCacheStore::class.java)

    /** 放宽读/调用超时，避免大 APK 下载被共享 client 的 10s 读超时打断。 */
    private val client = okHttpClient.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    private val inflight = ConcurrentHashMap<String, CompletableFuture<Path>>()

    /** 解析某个下载地址对应的本地 APK 文件（命中缓存且未过期直接返回，否则拉取）。 */
    fun resolve(downloadUrl: String): Path {
        val key = Sha256.of(downloadUrl.toByteArray())
        val dir = Path.of(props.apkCacheDir)
        Files.createDirectories(dir)
        val cached = dir.resolve(key)
        if (Files.exists(cached) && isFresh(cached)) {
            return cached
        }
        try {
            inflight.computeIfAbsent(key) {
                CompletableFuture.supplyAsync { doFetch(downloadUrl, cached) }
            }.join()
        } finally {
            inflight.remove(key)
        }
        if (!Files.exists(cached)) {
            throw ApkFetchException("APK 拉取后文件缺失：$downloadUrl")
        }
        sweep()
        return cached
    }

    private fun doFetch(url: String, target: Path): Path {
        val request = okhttp3.Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw ApkFetchException("拉取 APK 失败 HTTP ${resp.code}：$url")
            }
            val body = resp.body
                ?: throw ApkFetchException("APK 响应体为空：$url")
            body.byteStream().use { input ->
                Files.newOutputStream(target).use { out -> input.copyTo(out, 8192) }
            }
        }
        log.info("APK 已缓存 key={} <- {}", target.fileName, url)
        return target
    }

    private fun isFresh(file: Path): Boolean {
        val ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis()
        return ageMs < props.apkTtlMin * 60_000
    }

    /** 惰性清理过期 APK 缓存（写入时顺带扫一遍，仅删过期项，避免磁盘膨胀）。 */
    private fun sweep() {
        val dir = Path.of(props.apkCacheDir)
        if (!Files.isDirectory(dir)) return
        Files.list(dir).use { stream ->
            stream.forEach { f ->
                if (Files.isRegularFile(f) && !isFresh(f)) {
                    try { Files.deleteIfExists(f) } catch (e: Exception) { /* 忽略单文件清理失败 */ }
                }
            }
        }
    }
}
