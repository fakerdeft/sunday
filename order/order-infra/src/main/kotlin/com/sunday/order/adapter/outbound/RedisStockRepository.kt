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
        private const val HOTDEAL_KEY_PREFIX = "hotdeal:"
        private const val RESERVATION_KEY_PREFIX = "reservation:"
        private const val PURCHASED_USERS_KEY_PREFIX = "purchased_users:"
        private const val ORDER_STREAM_KEY = "order:stream"

        /**
         * Lua 스크립트: 재고 확인 후 차감
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

        /**
         * Lua 스크립트: 원자적 주문 처리
         * KEYS[1]: hotdeal:{productId} (Hash: stock, price, name)
         * KEYS[2]: purchased_users:{productId}
         * KEYS[3]: order:stream
         *
         * ARGV[1]: memberId
         * ARGV[2]: quantity
         * ARGV[3]: reservationKey
         * ARGV[4]: productId
         *
         * @return 1: 성공, 0: 재고 부족, -1: 중복 요청, -2: 상품 없음
         */
        private val PROCESS_ORDER_ATOMIC_SCRIPT = RedisScript.of<Long>(
            """
            -- 1. 상품 정보 조회 (Redis Hash)
            local productInfo = redis.call('HMGET', KEYS[1], 'stock', 'price', 'name')
            local stock = tonumber(productInfo[1])
            local price = productInfo[2]
            local name = productInfo[3]

            -- 상품이 없으면 -2 반환
            if not stock or not price or not name then
                return -2
            end

            -- 2. 중복 체크
            local isMember = redis.call('SISMEMBER', KEYS[2], ARGV[1])
            
            if isMember == 1 then
                return -1
            end

            -- 3. 재고 체크
            local quantity = tonumber(ARGV[2])
            
            if stock < quantity then
                return 0
            end

            -- 4. 재고 감소
            redis.call('HINCRBY', KEYS[1], 'stock', -quantity)

            -- 5. 구매자 목록에 추가
            redis.call('SADD', KEYS[2], ARGV[1])

            -- 6. 총액 계산 및 Stream 발행
            local totalAmount = tonumber(price) * quantity
            
            redis.call('XADD', KEYS[3], '*',
                'memberId', ARGV[1],
                'quantity', ARGV[2],
                'reservationKey', ARGV[3],
                'productId', ARGV[4],
                'productName', name,
                'unitPrice', price,
                'totalAmount', tostring(totalAmount))

            return 1
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

    override fun initializeHotDeal(productId: Long, stock: Int, price: String, name: String) {
        val key = hotdealKey(productId)
        redisTemplate.opsForHash<String, String>().putAll(
            key,
            mapOf(
                "stock" to stock.toString(),
                "price" to price,
                "name" to name
            )
        )
    }

    override fun processOrderAtomic(
        productId: Long,
        memberId: Long,
        quantity: Int,
        reservationKey: String
    ): Int {
        val result = redisTemplate.execute(
            PROCESS_ORDER_ATOMIC_SCRIPT,
            listOf(
                hotdealKey(productId),
                purchasedUsersKey(productId),
                ORDER_STREAM_KEY
            ),
            memberId.toString(),
            quantity.toString(),
            reservationKey,
            productId.toString()
        )

        return result?.toInt() ?: 0
    }

    override fun clearPurchasedUsers(productId: Long) {
        redisTemplate.delete(purchasedUsersKey(productId))
    }

    private fun stockKey(productId: Long) = "$STOCK_KEY_PREFIX$productId"
    private fun hotdealKey(productId: Long) = "$HOTDEAL_KEY_PREFIX$productId"
    private fun purchasedUsersKey(productId: Long) = "$PURCHASED_USERS_KEY_PREFIX$productId"
    private fun reservationKey(key: String) = "$RESERVATION_KEY_PREFIX$key"
    private fun generateReservationKey() = UUID.randomUUID().toString()
}
