// 대기열 서버는 자체 데이터베이스를 갖지 않는다.
// 루트 빌드가 모든 *-api 모듈에 넣어 주는 JPA/JDBC 의존성을 여기서만 제외해,
// 데이터소스 자동 설정이 아예 동작하지 않도록 한다.
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-data-jpa")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-jdbc")
}

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-data-redis")
}
