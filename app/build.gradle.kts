// App - 실행 모듈 (설정은 루트 build.gradle.kts에서 관리)

dependencies {
    implementation(project(":common"))
    implementation(project(":member:member-infra"))
    implementation(project(":account:account-infra"))
    implementation(project(":payment:payment-infra"))
    implementation(project(":order:order-infra"))
}
