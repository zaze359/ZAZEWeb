plugins {
    id("java")
    id("org.springframework.boot") version "2.7.18"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"

	kotlin("plugin.lombok") version "1.8.21"
	id("io.freefair.lombok") version "5.3.0"
}

group = "com.zaze.server"
version = "0.0.1-SNAPSHOT"

kotlinLombok {
    lombokConfigurationFile(file("lombok.config"))
}

dependencies {

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
//    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    implementation("org.apache.commons:commons-pool2")

//    implementation(kotlin("stdlib-jdk8"))

//    implementation("org.joda:joda-money:1.0.3")
//    implementation("org.jadira.usertype:usertype.core:6.0.1.GA")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

//    runtimeOnly("com.h2database:h2:2.1.214")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    implementation(project(":core:database"))
    implementation(project(":core:common"))
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
