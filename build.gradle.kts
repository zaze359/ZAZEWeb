import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.7.18"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"

//	kotlin("plugin.lombok") version "1.8.21"
//	id("io.freefair.lombok") version "5.3.0"
}

group = "com.zaze.server"
version = "0.0.1-SNAPSHOT"
description = "demo"

java.sourceCompatibility = JavaVersion.VERSION_11
java.targetCompatibility = JavaVersion.VERSION_11

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    //
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-actuator")


    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")


//    implementation("com.github.pagehelper:pagehelper-spring-boot-starter:1.4.6")
//    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    runtimeOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // module
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":feature:showcase"))
    implementation(project(":feature:application"))
    implementation(project(":feature:ad"))
    implementation(project(":feature:message"))
    implementation(project(":feature:appmarket"))
    implementation(project(":feature:auth"))

    // 停在 4.10.0：4.11+ 需 Kotlin 1.8+ 编译环境，本项目 Kotlin 是 1.6.21（详见 core/network 注释）
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    // ---- 安全版本覆盖 ----
    // 注意：子模块里显式声明的版本，在根 project 会被 dependency-management 的 BOM 版本覆盖
    // （对根来说它们只是传递依赖），所以下面这几项必须在**根**再声明一遍，与 okhttp 同一套路，
    // 否则打进 boot jar 的仍是 BOM 里的旧版本。
    implementation("com.google.code.gson:gson:2.13.2")
    runtimeOnly("com.h2database:h2:2.3.232")
    implementation("org.springframework.security:spring-security-crypto:5.7.14")
    implementation("ch.qos.logback:logback-core:1.2.13")

    // ---- 下面这些没有子模块声明，属于纯传递依赖的版本固定 ----
    // snakeyaml 1.33 是 1.x 线最后一版，修 CVE-2022-25857(HIGH) 与 CVE-2022-38749/38750/38751/38752、
    // CVE-2022-41854(MODERATE)。不能升 2.x —— Spring Boot 2.7 的 YamlPropertiesFactoryBean 依赖 1.x API。
    // 残留：CVE-2022-1471(HIGH) 修复版本是 2.0，与 Boot 2.7 不兼容，暂不处理。
    implementation("org.yaml:snakeyaml:1.33")
    // logback 1.2.13 是 1.2 线最后一版，修 CVE-2023-6378 / CVE-2023-6481(HIGH)。
    // 不能升 1.3+/1.5+ —— 需要 SLF4J 2.x，而 Boot 2.7 用的是 SLF4J 1.7.x。
    // 残留：2025/2026 年的 logback CVE 修复版本在 1.5.x，同样不兼容，暂不处理。
    implementation("ch.qos.logback:logback-classic:1.2.13")

}

//sourceSets {
//    main {
//        java {
//            srcDirs(
//                "/src/main/gen", "/src/main/java", "/src/main/kotlin"
//            )
//        }
//    }
//}

tasks.withType<KotlinCompile> {
    kotlinOptions {
//        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
