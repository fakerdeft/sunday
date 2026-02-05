// Payment Infra - Repository Adapter (Outbound Adapter)

dependencies {
    implementation(project(":payment:payment-core"))
    implementation(project(":common"))
    implementation(project(":account:account-core"))
    implementation(project(":order:order-core"))
    implementation(project(":outbox"))
}
