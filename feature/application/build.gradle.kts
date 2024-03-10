plugins {
    id("java")
    id("org.springframework.boot") version "2.3.1.RELEASE"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
}

group = "com.zaze.server"
version = "0.0.1-SNAPSHOT"

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
//    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
//    implementation("org.springframework.boot:spring-boot-starter-web")

//    implementation("org.apache.commons:commons-pool2")

    implementation(project(":core:common"))
    implementation(project(":core:database"))

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