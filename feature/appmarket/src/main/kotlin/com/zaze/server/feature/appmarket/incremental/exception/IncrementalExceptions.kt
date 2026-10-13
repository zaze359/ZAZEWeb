package com.zaze.server.feature.appmarket.incremental.exception

/**
 * 差分包不可用（体积超阈 / 上游不可达 / 自校验失败 / 超时）。
 * 语义上**不是错误**，而应引导客户端回退到完整 APK 下载，因此统一转 [com.zaze.server.common.controller.Response](200, null, reason)。
 */
class PatchUnavailableException(message: String) : RuntimeException(message)

/** bsdiff/bspatch 算法执行失败（库异常、自校验不一致等）。 */
class BsdiffException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 从下载源拉取 APK 字节失败。 */
class ApkFetchException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
