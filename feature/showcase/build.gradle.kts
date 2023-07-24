plugins {
    id("java")
    id("org.springframework.boot") version "2.3.1.RELEASE"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"

	kotlin("plugin.lombok") version "1.8.21"
	id("io.freefair.lombok") version "5.3.0"
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
tasks.jar {
    enabled = true
}