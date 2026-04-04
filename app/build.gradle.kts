// App - 실행 모듈 (설정은 루트 build.gradle.kts에서 관리)

dependencies {
    implementation(project(":common"))
    implementation(project(":support-infra"))

    // Member (마이그레이션 완료 → member-api 독립 서버로 분리됨)
    // implementation(project(":member:member-api"))
    // implementation(project(":member:member-infra"))

    // Account (마이그레이션 완료 → account-api 독립 서버로 분리됨)
    // implementation(project(":account:account-api"))
    // implementation(project(":account:account-infra"))

    // Payment (마이그레이션 완료 → payment-api 독립 서버로 분리됨)
    // implementation(project(":payment:payment-api"))
    // implementation(project(":payment:payment-infra"))

    // Order (마이그레이션 완료 → order-api 독립 서버로 분리됨)
    // implementation(project(":order:order-core"))
    // implementation(project(":order:order-api"))
    // implementation(project(":order:order-infra"))

    // Monitoring
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}
