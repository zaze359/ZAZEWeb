package com.zaze.server.feature.appmarket.incremental.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 增量更新相关配置（前缀 `incremental`）。
 *
 * 全部带默认值，无需在 application.properties 中预置；如需覆盖可用
 * `-Dincremental.patchDir=...` 等系统属性，或在 application.properties 写 `incremental.*`。
 */
@Configuration
@ConfigurationProperties(prefix = "incremental")
class IncrementalProperties {
    /** 差分包缓存根目录（patch 文件 + sidecar json） */
    var patchDir: String = "./data/incremental/patches"

    /** 临时 APK 缓存根目录（从下载源拉取的 APK 字节，带过期清理，不长期落盘） */
    var apkCacheDir: String = "./data/incremental/apk"

    /** 单包体积上限（MB）：old 或 new 超过则跳过 diff，走整包下载降级 */
    var maxApkMb: Long = 200

    /** diff 计算超时（秒）：超时返回降级信封，不挂起请求线程 */
    var diffTimeoutSec: Long = 300

    /** 临时 APK 缓存过期时间（分钟） */
    var apkTtlMin: Long = 1440
}
