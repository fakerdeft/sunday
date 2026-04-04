package com.sunday.order.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class Order(
    val id: Long,
    val memberId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalAmount: BigDecimal,
    val status: OrderStatus,
    val reservationKey: String,
    val expireAt: LocalDateTime,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        if (quantity <= 0) throw InvalidOrderQuantityException(quantity)
        if (unitPrice <= BigDecimal.ZERO) throw InvalidProductPriceException(unitPrice)
    }

    companion object {
        private const val RESERVATION_TIMEOUT_MINUTES = 5L

        fun create(memberId: Long, product: Product, quantity: Int, reservationKey: String): Order {
            val now = LocalDateTime.now()
            return Order(
                id = 0L,
                memberId = memberId,
                productId = product.id,
                productName = product.name,
                quantity = quantity,
                unitPrice = product.price,
                totalAmount = product.price * BigDecimal(quantity),
                status = OrderStatus.PENDING,
                reservationKey = reservationKey,
                expireAt = now.plusMinutes(RESERVATION_TIMEOUT_MINUTES),
                createdAt = now,
                updatedAt = now
            )
        }
    }

    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expireAt)

    fun markAsPaid(): Order {
        if (status != OrderStatus.PENDING) throw InvalidOrderStatusException(id, status.name, "PENDING")
        if (isExpired()) throw OrderExpiredException(id)
        return copy(status = OrderStatus.PAID, updatedAt = LocalDateTime.now())
    }

    fun markAsCancelled(): Order {
        if (status != OrderStatus.PENDING && status != OrderStatus.PAID)
            throw InvalidOrderStatusException(id, status.name, "PENDING or PAID")
        return copy(status = OrderStatus.CANCELLED, updatedAt = LocalDateTime.now())
    }

    fun markAsExpired(): Order {
        if (status != OrderStatus.PENDING) throw InvalidOrderStatusException(id, status.name, "PENDING")
        return copy(status = OrderStatus.EXPIRED, updatedAt = LocalDateTime.now())
    }
}
