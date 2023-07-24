import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.3.1.RELEASE"
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

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}


repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
    mavenLocal()
    mavenCentral()
}

dependencies {

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    //
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-actuator")


    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(kotlin("stdlib-jdk8"))

//    implementation("com.github.pagehelper:pagehelper-spring-boot-starter:1.4.6")
//    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    runtimeOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // module
    implementation(project(":core:common"))
    implementation(project(":feature:showcase"))
    implementation(project(":feature:application"))
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
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
