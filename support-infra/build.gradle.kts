import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Redisson
    implementation("org.redisson:redisson-spring-boot-starter:4.1.0")

    // AOP (DistributedLockAspect, AopForTransaction, CustomSpringELParser)
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
}

// 라이브러리 모듈 - 실행 불가
tasks.named<BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
