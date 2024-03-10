package com.zaze.server.core.network.anno

import com.zaze.server.core.network.config.OkHttpConfiguration
import org.springframework.context.annotation.Import


@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Import(
    OkHttpConfiguration::class
)
annotation class EnableOkHttp