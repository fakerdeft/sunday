// Payment Infra - Spring Boot, JPA, Redis 구현

dependencies {
    implementation(project(":payment:payment-core"))
    implementation(project(":common"))
    implementation(project(":account:account-core"))
    implementation(project(":order:order-core"))
}
