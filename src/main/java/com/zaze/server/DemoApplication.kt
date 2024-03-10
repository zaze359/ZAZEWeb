package com.zaze.server

import com.zaze.server.core.network.anno.EnableOkHttp
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

//@SpringBootApplication
@SpringBootApplication(scanBasePackages = ["com.zaze.server"])
//@ComponentScan("com.zaze.server.feature.message")
@EnableOkHttp
@EnableJpaRepositories
//@EnableAspectJAutoProxy
//@EnableCaching(proxyTargetClass = true)
class DemoApplication {

}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
