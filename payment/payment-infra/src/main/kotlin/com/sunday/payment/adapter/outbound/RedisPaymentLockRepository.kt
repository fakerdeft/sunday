package com.sunday.payment.adapter.outbound

import com.sunday.payment.port.outbound.PaymentLockRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Redis 기반 결제 분산 락 구현
 *
 * - SETNX를 이용한 분산 락
 * - 멱등성 키 관리
 */
@Component
class RedisPaymentLockRepository(
    private val redisTemplate: StringRedisTemplate
) : PaymentLockRepository {

    companion object {
        private const val PAYMENT_LOCK_PREFIX = "payment:lock:order:"
        private const val IDEMPOTENCY_KEY_PREFIX = "payment:idempotency:"

        /**
         * Lua 스크립트: 락 해제 (자신이 설정한 락만 해제)
         */
        private val RELEASE_LOCK_SCRIPT = RedisScript.of<Long>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """.trimIndent(),
            Long::class.java
        )

        private const val LOCK_VALUE = "locked"
    }

    override fun acquireLock(orderId: Long, ttlSeconds: Long): Boolean {
        val key = lockKey(orderId)
        val result = redisTemplate.opsForValue().setIfAbsent(
            key,
            LOCK_VALUE,
            Duration.ofSeconds(ttlSeconds)
        )

        return result == true
    }

    override fun releaseLock(orderId: Long) {
        val key = lockKey(orderId)
        redisTemplate.execute(
            RELEASE_LOCK_SCRIPT,
            listOf(key),
            LOCK_VALUE
        )
    }

    override fun registerIdempotencyKey(idempotencyKey: String, ttlSeconds: Long): Boolean {
        val key = idempotencyKey(idempotencyKey)
        val result = redisTemplate.opsForValue().setIfAbsent(
            key,
            "1",
            Duration.ofSeconds(ttlSeconds)
        )

        return result == true
    }

    override fun existsIdempotencyKey(idempotencyKey: String): Boolean {
        val key = idempotencyKey(idempotencyKey)

        return redisTemplate.hasKey(key)
    }

    private fun lockKey(orderId: Long) = "$PAYMENT_LOCK_PREFIX$orderId"
    private fun idempotencyKey(key: String) = "$IDEMPOTENCY_KEY_PREFIX$key"
}
