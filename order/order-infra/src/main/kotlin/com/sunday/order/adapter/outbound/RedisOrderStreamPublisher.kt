package com.sunday.order.adapter.outbound

import com.sunday.order.port.outbound.OrderStreamPublisher
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisOrderStreamPublisher(
    private val redisTemplate: StringRedisTemplate
) : OrderStreamPublisher {

    companion object {
        const val STREAM_KEY = "order:stream"
    }

    override fun publishOrderCreated(
        reservationKey: String,
        memberId: Long,
        productId: Long,
        productName: String,
        quantity: Int,
        unitPrice: String,
        totalAmount: String
    ): String {
        val message = mapOf(
            "reservationKey" to reservationKey,
            "memberId" to memberId.toString(),
            "productId" to productId.toString(),
            "productName" to productName,
            "quantity" to quantity.toString(),
            "unitPrice" to unitPrice,
            "totalAmount" to totalAmount
        )

        val recordId: RecordId? = redisTemplate.opsForStream<String, String>()
            .add(STREAM_KEY, message)

        return recordId?.value ?: throw RuntimeException("주문 스트림 발행 실패")
    }
}
