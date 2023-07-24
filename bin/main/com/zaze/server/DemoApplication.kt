package com.zaze.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.zaze.server"])
@EnableJpaRepositories
//@EnableAspectJAutoProxy
//@EnableCaching(proxyTargetClass = true)
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}