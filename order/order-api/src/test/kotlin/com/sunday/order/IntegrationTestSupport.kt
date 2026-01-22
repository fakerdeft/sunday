package com.sunday.order

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer

class IntegrationTestSupport : ApplicationContextInitializer<ConfigurableApplicationContext> {

    companion object {
        private const val REDIS_IMAGE = "redis:latest"
        private const val POSTGRES_IMAGE = "postgres:latest"

        val REDIS_CONTAINER: GenericContainer<*> = GenericContainer(REDIS_IMAGE)
            .withExposedPorts(6379)
            .withReuse(true)

        val POSTGRES_CONTAINER: PostgreSQLContainer<*> = PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("sunday_order_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        REDIS_CONTAINER.start()
        POSTGRES_CONTAINER.start()

        TestPropertyValues.of(
            "spring.data.redis.host=${REDIS_CONTAINER.host}",
            "spring.data.redis.port=${REDIS_CONTAINER.getMappedPort(6379)}",
            "spring.datasource.url=${POSTGRES_CONTAINER.jdbcUrl}",
            "spring.datasource.username=${POSTGRES_CONTAINER.username}",
            "spring.datasource.password=${POSTGRES_CONTAINER.password}",
            "spring.datasource.driver-class-name=${POSTGRES_CONTAINER.driverClassName}"
        ).applyTo(applicationContext.environment)
    }
}