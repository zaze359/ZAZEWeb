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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("org.apache.commons:commons-pool2")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation(project(":core:common"))
    implementation(project(":core:database"))
    // 自动采集依赖 OkHttp（由 OkHttpConfiguration / @EnableOkHttp 提供 OkHttpClient bean）。
    // 显式声明与 root / core:network 一致的 4.10.0：避免经 core:network 传递时被 Spring Boot BOM 降级到 3.14.9，
    // 导致编译期与运行时（jar 内为 4.10.0）版本不一致。
    implementation(project(":core:network"))
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
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
