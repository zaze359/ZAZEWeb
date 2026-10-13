package com.zaze.server.feature.appmarket.collector

import okhttp3.Interceptor
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * 上游 HTTP 请求日志拦截器：让「服务端到底发起了什么请求」在日志里可见。
 *
 * **背景（为什么需要它）**：此前日志只有控制器进出与最终 `Response{...}`，
 * 中间究竟请求了哪个 URL、返回什么状态码、耗时多少**完全不可见**，
 * 排查「某上游未命中」时只能靠猜——本次应用宝域名写错就是靠手工 curl 才定位到的。
 *
 * **只记录元信息**：方法 / URL / 状态码 / 耗时 / 响应字节数。
 * **绝不读取或打印响应体** —— 应用宝详情页与 APK 接口单响应就有 350~400KB，
 * 打印 body 会瞬间刷爆日志；且读取 body 会破坏下游的流式消费（ResponseBody 只能消费一次）。
 *
 * 用法：挂到外部客户端的**公共父客户端**上即可，派生客户端（`newBuilder()`）会自动继承本拦截器。
 */
object UpstreamLoggingInterceptor : Interceptor {

    private val log = LoggerFactory.getLogger(UpstreamLoggingInterceptor::class.java)

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val t0 = System.nanoTime()
        return try {
            val resp = chain.proceed(req)
            val ms = (System.nanoTime() - t0) / 1_000_000
            // contentLength() 不消费 body；分块传输时为 -1
            val len = resp.body?.contentLength() ?: -1L
            log.info(
                "[上游] {} {} -> HTTP {} ({}ms, {})",
                req.method, req.url, resp.code, ms,
                if (len >= 0) "$len bytes" else "长度未知"
            )
            resp
        } catch (e: Exception) {
            val ms = (System.nanoTime() - t0) / 1_000_000
            log.warn(
                "[上游] {} {} -> 请求失败 ({}ms): {}: {}",
                req.method, req.url, ms, e.javaClass.simpleName, e.message
            )
            throw e
        }
    }
}
