plugins {
    id("java")
    id("org.springframework.boot") version "2.7.18"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
}

group = "com.zaze.server"
version = "0.0.1-SNAPSHOT"

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // spring starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // 密码哈希：只引入 crypto 这个独立小依赖，不引入完整 Spring Security
    // 只做 BCrypt 加解密。5.7.14 是 5.7 线公开仓库的最高版本（BOM 2.7.18 托管 5.7.11）。
    // 注：CVE-2025-22228 的修复版本是 5.7.16/5.8.18，前者未公开发布、后者与 Boot 2.7 不兼容 → 残留。
    implementation("org.springframework.security:spring-security-crypto:5.7.14")

    implementation(project(":core:common"))
    implementation(project(":core:database"))
}

tasks.getByName<Test>("test") {
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
