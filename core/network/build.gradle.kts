plugins {
    id("java")
    id("org.springframework.boot") version "2.7.18"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"

}

group = "com.zaze.server.feature"
version = "0.0.1-SNAPSHOT"

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    api("org.springframework.boot:spring-boot-starter-web")
    // okhttp 必须显式写死版本，否则会被 BOM 2.7.18 降级到 4.9.3。
    // 停在 4.10.0：okhttp 4.11+ 由 Kotlin 1.8 编译（metadata 1.8.0），而本项目 Kotlin 编译器是 1.6.21，
    // 编译会报 "Class 'okhttp3.Request' was compiled with an incompatible version of Kotlin"。
    // 要升到 4.12.0 必须先升 Kotlin 编译器（另立任务）。
    // 残留：okio 3.0.0 的 CVE-2023-3635（MODERATE，有符号/无符号转换错误），修复版本 3.4.0 同样需 Kotlin 1.9。
    api("com.squareup.okhttp3:okhttp:4.10.0")
}

tasks.test {
    useJUnitPlatform()
}
tasks.bootJar {
    enabled = false
}
// 库模块没有 main class：禁用 bootRun，避免 `./gradlew bootRun` 因 mainClass 无法解析而失败。
tasks.bootRun {
    enabled = false
}
tasks.jar {
    enabled = true
}

// 与 root 保持一致的 Java 11 目标：
// 各模块若不显式声明，会跟随构建机当前 JVM（这里是 17）产出 Java 17 的变体，
// 导致 root（要求 11）依赖解析失败。显式声明后构建结果与本机 JDK 版本无关。
java.sourceCompatibility = JavaVersion.VERSION_11
java.targetCompatibility = JavaVersion.VERSION_11

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}
