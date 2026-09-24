plugins {
    id("java-library")
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

    // spring boot 基础依赖
    api("org.springframework.boot:spring-boot-starter-web")
//    api("org.springframework.boot:spring-boot-starter-json")
    api("com.google.code.gson:gson:2.8.6")

    api("org.aspectj:aspectjweaver:1.9.19")

    // kotlin
    api("org.jetbrains.kotlin:kotlin-reflect")
    api(kotlin("stdlib-jdk8"))

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.bootJar {
    enabled = false
}
// 库模块没有 main class：禁用 bootRun，否则在根项目执行 `./gradlew bootRun` 时
// Gradle 会为每个子项目都执行同名任务，导致 core:common:bootRun 因无法解析 mainClass 而失败。
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
