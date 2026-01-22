package com.sunday.support.infra.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedissonConfig(
    @Value("\${spring.data.redis.host}") private val redisHost: String,
    @Value("\${spring.data.redis.port}") private val redisPort: Int
) {
    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()

        config.useSingleServer()
            .setAddress("redis://$redisHost:$redisPort")
            .setConnectionMinimumIdleSize(1).connectionPoolSize = 10

        return Redisson.create(config)
    }
}