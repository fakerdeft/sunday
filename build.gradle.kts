import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot") version "4.0.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.spring") version "2.3.0" apply false
    kotlin("plugin.jpa") version "2.3.0" apply false
    kotlin("kapt") version "2.3.0" apply false
}

// 프로젝트 전체 적용
allprojects {
    group = "com.sunday"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

val queryDslVersion = "7.0"

// 서브모듈 기본 설정
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")

    // Logback 제외 (Log4j2 사용)
    configurations.all {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }

    // 모든 모듈에서 기본으로 필요한 의존성만 추가
    dependencies {
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")

        // Test
        "testImplementation"("org.junit.jupiter:junit-jupiter:6.1.0-M1")
        "testImplementation"("io.kotest:kotest-runner-junit5:6.1.0")
        "testImplementation"("io.kotest:kotest-assertions-core:6.1.0")
        "testImplementation"("io.kotest:kotest-extensions-spring:6.1.0")
        "testImplementation"("io.mockk:mockk:1.14.7")
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

// ================================
// Core 모듈 (Domain + Application Service)
// ================================
configure(subprojects.filter { it.name.endsWith("-core") }) {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")

    // 라이브러리 모듈 - 실행 불가
    tasks.named<BootJar>("bootJar") {
        enabled = false
    }

    tasks.named<Jar>("jar") {
        enabled = true
    }
}

// ================================
// API 모듈 (Inbound Adapter)
// ================================
configure(subprojects.filter { it.name.endsWith("-api") }) {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")

    dependencies {
        // Spring Boot
        "implementation"("org.springframework.boot:spring-boot-starter-web")
        "implementation"("org.springframework.boot:spring-boot-starter-validation")

        // Log4j2
        "implementation"("org.springframework.boot:spring-boot-starter-log4j2")
        "implementation"("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

        // JSON
        "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")

        // Test
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"("org.testcontainers:testcontainers:2.0.3")
        "testImplementation"("org.testcontainers:junit-jupiter:1.21.4")
        "testImplementation"("org.testcontainers:postgresql:1.21.4")
    }

    // 라이브러리 모듈 - 실행 불가
    tasks.named<BootJar>("bootJar") {
        enabled = false
    }

    tasks.named<Jar>("jar") {
        enabled = true
    }
}

// ================================
// Infra 모듈 (Outbound Adapter)
// ================================
configure(subprojects.filter { it.name.endsWith("-infra") }) {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.jetbrains.kotlin.kapt")

    dependencies {
        // Spring Boot
        "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
        "implementation"("org.springframework.boot:spring-boot-starter-data-redis")

        // Log4j2
        "implementation"("org.springframework.boot:spring-boot-starter-log4j2")
        "implementation"("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

        // PostgreSQL
        "runtimeOnly"("org.postgresql:postgresql")

        // QueryDSL
        "implementation"("io.github.openfeign.querydsl:querydsl-jpa:$queryDslVersion")
        "implementation"("io.github.openfeign.querydsl:querydsl-core:$queryDslVersion")
        "kapt"("io.github.openfeign.querydsl:querydsl-apt:$queryDslVersion:jakarta")
        "kapt"("jakarta.annotation:jakarta.annotation-api")
        "kapt"("jakarta.persistence:jakarta.persistence-api")

        // Test
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }

    // 라이브러리 모듈 - 실행 불가
    tasks.named<BootJar>("bootJar") {
        enabled = false
    }

    tasks.named<Jar>("jar") {
        enabled = true
    }
}

// ================================
// Batch 모듈 (Scheduler Jobs)
// ================================
configure(subprojects.filter { it.name.endsWith("-batch") }) {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")

    dependencies {
        // Spring Boot
        "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
        "implementation"("org.springframework.boot:spring-boot-starter-data-redis")

        // Log4j2
        "implementation"("org.springframework.boot:spring-boot-starter-log4j2")
        "implementation"("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

        // PostgreSQL
        "runtimeOnly"("org.postgresql:postgresql")

        // Test
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }

    // 라이브러리 모듈 - 실행 불가
    tasks.named<BootJar>("bootJar") {
        enabled = false
    }

    tasks.named<Jar>("jar") {
        enabled = true
    }
}

// ================================
// App 모듈 (실행)
// ================================
configure(subprojects.filter { it.name == "app" }) {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")

    dependencies {
        // Spring Boot
        "implementation"("org.springframework.boot:spring-boot-starter-web")
        "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
        "implementation"("org.springframework.boot:spring-boot-starter-data-redis")

        // Log4j2
        "implementation"("org.springframework.boot:spring-boot-starter-log4j2")
        "implementation"("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

        // JSON
        "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")

        // PostgreSQL
        "runtimeOnly"("org.postgresql:postgresql")

        // QueryDSL
        "implementation"("io.github.openfeign.querydsl:querydsl-jpa:$queryDslVersion")

        // Test
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }
}
