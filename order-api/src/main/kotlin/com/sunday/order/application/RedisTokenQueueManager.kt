package com.sunday.order.application

import com.sunday.order.repository.ProductRepository
import jakarta.annotation.PostConstruct
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisTokenQueueManager(
    private val redisTemplate: StringRedisTemplate,
    private val productRepository: ProductRepository
) {
    companion object {
        private const val KEY_PREFIX = "stock:queue:"
    }

    @PostConstruct
    fun init() {
        productRepository.findAll().forEach { p -> initQueue(p.id, p.stock) }
    }

    fun initQueue(productId: Long, stock: Int) {
        val key = key(productId)
        redisTemplate.delete(key)
        if (stock > 0) {
            val tokens = (1..stock).map { "$productId:$it" }.toTypedArray()
            redisTemplate.opsForList().rightPushAll(key, *tokens)
        }
    }

    /** 토큰 하나를 원자적으로 선점. null이면 재고 없음. */
    fun claim(productId: Long): String? =
        redisTemplate.opsForList().leftPop(key(productId))

    /** 취소/만료 시 토큰 반환 */
    fun release(productId: Long) {
        redisTemplate.opsForList().rightPush(key(productId), "$productId:returned")
    }

    fun queueSize(productId: Long): Long =
        redisTemplate.opsForList().size(key(productId)) ?: 0L

    private fun key(productId: Long) = "$KEY_PREFIX$productId"
}
