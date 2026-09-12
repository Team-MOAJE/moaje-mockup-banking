plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("org.springframework.boot") version "3.3.6"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "banking-mockup"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

springBoot {
    mainClass.set("com.example.bankingmockup.BankingMockupApplicationKt")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 실제 Redis가 필요한 검증은 별도 명령으로 실행한다. 일반 단위 테스트를 임의로 Disabled 처리하지 않는다.
tasks.named<Test>("test") { useJUnitPlatform { excludeTags("redis") } }
tasks.register<Test>("redisIntegrationTest") {
    description = "Runs ledger Lua and snapshot cursor tests against a running Redis."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("redis") }
}

tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveFileName.set("banking-mockup.jar")
}
