package com.sunday.order.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

@Repository
class RedisStockRepository(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private const val STOCK_KEY_PREFIX = "stock:product:"
        private const val HOTDEAL_KEY_PREFIX = "hotdeal:"
        private const val RESERVATION_KEY_PREFIX = "reservation:"
        private const val PURCHASED_USERS_KEY_PREFIX = "purchased_users:"
        const val ORDER_STREAM_KEY = "order:stream"

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
         * KEYS[1]: hotdeal:{productId}, KEYS[2]: purchased_users:{productId}, KEYS[3]: order:stream
         * ARGV[1]: memberId, ARGV[2]: quantity, ARGV[3]: reservationKey, ARGV[4]: productId
         * @return 1: 성공, 0: 재고 부족, -1: 중복 요청, -2: 상품 없음
         */
        private val PROCESS_ORDER_ATOMIC_SCRIPT = RedisScript.of<Long>(
            """
            local productInfo = redis.call('HMGET', KEYS[1], 'stock', 'price', 'name')
            local stock = tonumber(productInfo[1])
            local price = productInfo[2]
            local name = productInfo[3]
            if not stock or not price or not name then
                return -2
            end
            local isMember = redis.call('SISMEMBER', KEYS[2], ARGV[1])
            if isMember == 1 then
                return -1
            end
            local quantity = tonumber(ARGV[2])
            if stock < quantity then
                return 0
            end
            redis.call('HINCRBY', KEYS[1], 'stock', -quantity)
            redis.call('SADD', KEYS[2], ARGV[1])
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

    fun initializeStock(productId: Long, quantity: Int) {
        redisTemplate.opsForValue().set(stockKey(productId), quantity.toString())
    }

    fun initializeHotDeal(productId: Long, stock: Int, price: String, name: String) {
        redisTemplate.opsForHash<String, String>().putAll(
            hotdealKey(productId),
            mapOf("stock" to stock.toString(), "price" to price, "name" to name)
        )
    }

    fun getStock(productId: Long): Int {
        return redisTemplate.opsForValue().get(stockKey(productId))?.toIntOrNull() ?: 0
    }

    fun decreaseStock(productId: Long, quantity: Int): Int? {
        val result = redisTemplate.execute(DECREASE_STOCK_SCRIPT, listOf(stockKey(productId)), quantity.toString())
        return if (result != null && result >= 0) result.toInt() else null
    }

    fun increaseStock(productId: Long, quantity: Int, maxStock: Int? = null): Int {
        val result = redisTemplate.execute(
            INCREASE_STOCK_SCRIPT,
            listOf(stockKey(productId)),
            quantity.toString(),
            maxStock?.toString() ?: "-1"
        )
        return result?.toInt() ?: quantity
    }

    fun createReservation(productId: Long, memberId: Long, quantity: Int, ttlSeconds: Long): String {
        val key = UUID.randomUUID().toString()
        redisTemplate.opsForValue().set(reservationKey(key), "$productId:$memberId:$quantity", Duration.ofSeconds(ttlSeconds))
        return key
    }

    fun getReservation(reservationKey: String): StockReservation? {
        val value = redisTemplate.opsForValue().get(this.reservationKey(reservationKey)) ?: return null
        val parts = value.split(":")
        if (parts.size != 3) return null
        return StockReservation(productId = parts[0].toLong(), memberId = parts[1].toLong(), quantity = parts[2].toInt())
    }

    fun releaseReservation(reservationKey: String): Boolean {
        return redisTemplate.delete(this.reservationKey(reservationKey))
    }

    fun processOrderAtomic(productId: Long, memberId: Long, quantity: Int, reservationKey: String): Int {
        val result = redisTemplate.execute(
            PROCESS_ORDER_ATOMIC_SCRIPT,
            listOf(hotdealKey(productId), purchasedUsersKey(productId), ORDER_STREAM_KEY),
            memberId.toString(), quantity.toString(), reservationKey, productId.toString()
        )
        return result?.toInt() ?: 0
    }

    fun clearPurchasedUsers(productId: Long) {
        redisTemplate.delete(purchasedUsersKey(productId))
    }

    private fun stockKey(productId: Long) = "$STOCK_KEY_PREFIX$productId"
    private fun hotdealKey(productId: Long) = "$HOTDEAL_KEY_PREFIX$productId"
    private fun purchasedUsersKey(productId: Long) = "$PURCHASED_USERS_KEY_PREFIX$productId"
    private fun reservationKey(key: String) = "$RESERVATION_KEY_PREFIX$key"
}
