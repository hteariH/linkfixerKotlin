plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.spring") version "2.1.10"
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("plugin.noarg") version "1.9.0"
}

noArg {
    annotation("org.springframework.data.mongodb.core.mapping.Document")
}

group = "com.mamoru"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("org.telegram:telegrambots:6.8.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("ch.qos.logback:logback-classic:1.4.12")
    implementation("com.google.genai:google-genai:1.0.0")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
