package com.sunday.order.application

import com.sunday.order.repository.ProductRepository
import jakarta.annotation.PostConstruct
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisStockCounter(
    private val redisTemplate: StringRedisTemplate,
    private val productRepository: ProductRepository
) {
    companion object {
        private const val KEY_PREFIX = "stock:counter:"
    }

    @PostConstruct
    fun init() {
        productRepository.findAll().forEach { p ->
            if (p.isHotDeal) set(p.id, p.stock)
        }
    }

    fun decrement(productId: Long, quantity: Int): Long =
        redisTemplate.opsForValue().decrement(key(productId), quantity.toLong()) ?: -1L

    fun increment(productId: Long, quantity: Int) {
        redisTemplate.opsForValue().increment(key(productId), quantity.toLong())
    }

    fun set(productId: Long, quantity: Int) {
        redisTemplate.opsForValue().set(key(productId), quantity.toString())
    }

    private fun key(productId: Long) = "$KEY_PREFIX$productId"
}
