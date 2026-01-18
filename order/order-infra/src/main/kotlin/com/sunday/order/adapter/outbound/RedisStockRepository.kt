package com.sunday.order.adapter.outbound

import com.sunday.order.port.outbound.StockRepository
import com.sunday.order.port.outbound.StockReservation
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*

/**
 * Redis 기반 재고 관리 구현
 *
 * - DECR/INCR을 이용한 원자적 재고 차감/복구
 * - Lua 스크립트로 재고 확인 + 차감 원자적 처리
 */
@Component
class RedisStockRepository(
    private val redisTemplate: StringRedisTemplate
) : StockRepository {

    companion object {
        private const val STOCK_KEY_PREFIX = "stock:product:"
        private const val RESERVATION_KEY_PREFIX = "reservation:"

        /**
         * Lua 스크립트: 재고 확인 후 차감 (원자적)
         * - 현재 재고 >= 요청 수량이면 차감 후 남은 수량 반환
         * - 재고 부족이면 -1 반환
         */
        private val DECREASE_STOCK_SCRIPT = RedisScript.of<Long>(
            """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            local quantity = tonumber(ARGV[1])
            if stock >= quantity then
                return redis.call('DECRBY', KEYS[1], quantity)
            else
                return -1
            end
            """.trimIndent(),
            Long::class.java
        )

        /**
         * Lua 스크립트: 재고 증가 (최대 재고 제한)
         * - ARGV[1]: 증가시킬 수량
         * - ARGV[2]: 최대 재고량 (없으면 -1)
         * 
         * 로직:
         * 1. 현재 재고 조회
         * 2. 최대 재고 제한이 있고 (-1이 아니고), (현재 재고 + 증가량 > 최대 재고) 이면
         *    -> 증가시키지 않고 현재 재고 반환 (또는 최대 재고로 맞춤? 여기서는 증가 안 함)
         * 3. 아니면 INCRBY 수행
         */
        private val INCREASE_STOCK_SCRIPT = RedisScript.of<Long>(
            """
            local current_stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            local quantity = tonumber(ARGV[1])
            local max_stock = tonumber(ARGV[2])
            
            if max_stock ~= -1 and (current_stock + quantity > max_stock) then
                return current_stock
            end
            
            return redis.call('INCRBY', KEYS[1], quantity)
            """.trimIndent(),
            Long::class.java
        )
    }

    override fun initializeStock(productId: Long, quantity: Int) {
        val key = stockKey(productId)
        redisTemplate.opsForValue().set(key, quantity.toString())
    }

    override fun getStock(productId: Long): Int {
        val key = stockKey(productId)

        return redisTemplate.opsForValue().get(key)?.toIntOrNull() ?: 0
    }

    override fun decreaseStock(productId: Long, quantity: Int): Int? {
        val key = stockKey(productId)
        val result = redisTemplate.execute(
            DECREASE_STOCK_SCRIPT,
            listOf(key),
            quantity.toString()
        )

        return if (result != null && result >= 0) result.toInt() else null
    }

    override fun increaseStock(productId: Long, quantity: Int, maxStock: Int?): Int {
        val key = stockKey(productId)
        val maxStockArg = maxStock?.toString() ?: "-1"
        
        val result = redisTemplate.execute(
            INCREASE_STOCK_SCRIPT,
            listOf(key),
            quantity.toString(),
            maxStockArg
        )

        return result?.toInt() ?: quantity
    }

    override fun createReservation(
        productId: Long,
        memberId: Long,
        quantity: Int,
        ttlSeconds: Long
    ): String {
        val reservationKey = generateReservationKey()
        val value = "$productId:$memberId:$quantity"

        redisTemplate.opsForValue().set(
            reservationKey(reservationKey),
            value,
            Duration.ofSeconds(ttlSeconds)
        )

        return reservationKey
    }

    override fun getReservation(reservationKey: String): StockReservation? {
        val value = redisTemplate.opsForValue().get(reservationKey(reservationKey))
            ?: return null

        val parts = value.split(":")
        if (parts.size != 3) return null

        return StockReservation(
            productId = parts[0].toLong(),
            memberId = parts[1].toLong(),
            quantity = parts[2].toInt()
        )
    }

    override fun releaseReservation(reservationKey: String): Boolean {
        return redisTemplate.delete(reservationKey(reservationKey))
    }

    private fun stockKey(productId: Long) = "$STOCK_KEY_PREFIX$productId"
    private fun reservationKey(key: String) = "$RESERVATION_KEY_PREFIX$key"
    private fun generateReservationKey() = UUID.randomUUID().toString()
}
