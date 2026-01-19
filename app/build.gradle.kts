// App - 실행 모듈 (설정은 루트 build.gradle.kts에서 관리)

dependencies {
    implementation(project(":common"))

    // Member
    implementation(project(":member:member-api"))
    implementation(project(":member:member-infra"))

    // Account
    implementation(project(":account:account-api"))
    implementation(project(":account:account-infra"))

    // Payment
    implementation(project(":payment:payment-api"))
    implementation(project(":payment:payment-infra"))

    // Order
    implementation(project(":order:order-api"))
    implementation(project(":order:order-infra"))
}
