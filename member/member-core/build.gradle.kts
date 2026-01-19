// Member Core - Domain + Application Service

dependencies {
    implementation(project(":common"))

    // Spring 최소 의존성 (Service, Transaction)
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
}
