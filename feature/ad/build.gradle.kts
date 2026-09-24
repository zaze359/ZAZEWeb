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
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(project(":core:common"))
    implementation(project(":core:database"))
}

tasks.test {
    useJUnitPlatform()
}

// 库模块没有 main class：禁用 bootJar / bootRun，
// 否则在根项目执行 `./gradlew bootRun` 时会命中本模块旧的 Main.java 工具类。
tasks.bootJar {
    enabled = false
}
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
