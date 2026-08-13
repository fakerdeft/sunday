apply(plugin = "org.jetbrains.kotlin.kapt")

val queryDslVersion = "7.0"

dependencies {
    implementation(project(":common"))

    implementation("com.github.f4b6a3:tsid-creator:5.2.6")

    implementation("io.github.openfeign.querydsl:querydsl-jpa:$queryDslVersion")
    implementation("io.github.openfeign.querydsl:querydsl-core:$queryDslVersion")
    "kapt"("io.github.openfeign.querydsl:querydsl-apt:$queryDslVersion:jakarta")
    "kapt"("jakarta.annotation:jakarta.annotation-api")
    "kapt"("jakarta.persistence:jakarta.persistence-api")
}
