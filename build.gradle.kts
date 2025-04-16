plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.spring") version "2.1.10"
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
//    kotlin("plugin.allopen") version "2.1.10"
//    id("org.graalvm.buildtools.native") version "0.9.28"
    kotlin("plugin.noarg") version "1.9.0" // Add this plugin
    kotlin("plugin.allopen") version "1.9.0"

}

noArg {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
}


group = "com.mamoru"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    // Add Kotlin reflection library (required for Spring Data JPA)
    implementation("org.jetbrains.kotlin:kotlin-reflect")


    // Spring Boot dependencies
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("org.springframework:spring-context-support:5.3.30")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.h2database:h2")

    // If you're still using javax namespace instead of jakarta
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.3")


    implementation("org.telegram:telegrambots:6.8.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("ch.qos.logback:logback-classic:1.4.12")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20231013")

    implementation("com.google.genai:google-genai:0.3.0")


    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")

}


tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}