import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

    tasks.withType<JavaCompile> {
        options.release.set(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

// ================================
// 분산 서버 모듈 (레이어드 아키텍처 - 루트 레벨 -api 모듈)
// ================================
configure(subprojects.filter { it.name.endsWith("-api") }) {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")

    dependencies {
        "implementation"("org.springframework.boot:spring-boot-starter-web")
        "implementation"("org.springframework.boot:spring-boot-starter-validation")
        "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
        "implementation"("org.springframework.boot:spring-boot-starter-log4j2")
        "implementation"("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
        "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")
        "runtimeOnly"("org.postgresql:postgresql")
        "implementation"("org.springframework.boot:spring-boot-starter-actuator")

        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"("org.testcontainers:testcontainers:1.21.4")
        "testImplementation"("org.testcontainers:junit-jupiter:1.21.4")
        "testImplementation"("org.testcontainers:postgresql:1.21.4")
    }

    // 실행 가능한 서버 (bootJar 활성화)
    tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
        enabled = true
    }

    tasks.named<Jar>("jar") {
        enabled = false
    }

    // sunday-config 서브모듈 설정 파일 로드
    tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
        val moduleName = project.name
        val configDir = rootProject.projectDir.resolve("sunday-config/config/$moduleName")
        args("--spring.profiles.active=local")
        args("--spring.config.additional-location=${configDir.toURI()}/")
    }
}


// ================================
// Servers - 개별 서버 실행 태스크
// ================================
tasks.register("memberApi") {
    group = "servers"
    description = "Start member-api (port 8081)"
    dependsOn(":member-api:bootRun")
}

tasks.register("accountApi") {
    group = "servers"
    description = "Start account-api (port 8082)"
    dependsOn(":account-api:bootRun")
}

tasks.register("orderApi") {
    group = "servers"
    description = "Start order-api (port 8083)"
    dependsOn(":order-api:bootRun")
}

tasks.register("paymentApi") {
    group = "servers"
    description = "Start payment-api (port 8084)"
    dependsOn(":payment-api:bootRun")
}

