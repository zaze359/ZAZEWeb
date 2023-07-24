plugins {
    id("java")
    id("org.springframework.boot") version "2.3.1.RELEASE"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
}

group = "com.zaze.server"
version = "0.0.1-SNAPSHOT"

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

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    //
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    //
//    implementation("org.springframework.boot:spring-boot-starter-web")
//    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // h2
    runtimeOnly("com.h2database:h2:2.1.214")
    // joda money
    implementation("org.joda:joda-money:1.0.3")
    implementation("org.jadira.usertype:usertype.core:6.0.1.GA")
    // lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.bootJar {
    enabled = false
}
tasks.jar {
    enabled = true
}