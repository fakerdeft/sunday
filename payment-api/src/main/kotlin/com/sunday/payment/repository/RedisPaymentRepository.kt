package com.sunday.payment.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RedisPaymentRepository(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private const val IDEMPOTENCY_KEY_PREFIX = "payment:idempotency:"
    }

    fun registerIdempotencyKey(idempotencyKey: String, ttlSeconds: Long): Boolean {
        val key = "$IDEMPOTENCY_KEY_PREFIX$idempotencyKey"
        return redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds)) == true
    }
}
