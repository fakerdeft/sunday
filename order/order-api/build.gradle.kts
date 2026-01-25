// Order API - Inbound Adapter

dependencies {
    implementation(project(":common"))
    implementation(project(":order:order-core"))

    testImplementation(project(":order:order-infra"))
    testImplementation(project(":support-infra"))
}
