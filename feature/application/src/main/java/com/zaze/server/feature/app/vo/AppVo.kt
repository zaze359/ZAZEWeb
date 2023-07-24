package com.zaze.server.feature.app.vo

data class AppVo(
    val appName: String? = null,
    val packageName: String,
    val versionCode: Long = 0,
    val versionName: String? = null,
)
