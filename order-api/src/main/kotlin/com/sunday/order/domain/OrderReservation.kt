package com.sunday.order.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderReservation(
    val id: Long,
    val memberId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalAmount: BigDecimal,
    val status: ReservationStatus,
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
        private const val RESERVATION_TIMEOUT_MINUTES = 10L

        fun create(memberId: Long, product: Product, quantity: Int, reservationKey: String): OrderReservation {
            val now = LocalDateTime.now()
            return OrderReservation(
                id = 0L,
                memberId = memberId,
                productId = product.id,
                productName = product.name,
                quantity = quantity,
                unitPrice = product.price,
                totalAmount = product.price * BigDecimal(quantity),
                status = ReservationStatus.PENDING,
                reservationKey = reservationKey,
                expireAt = now.plusMinutes(RESERVATION_TIMEOUT_MINUTES),
                createdAt = now,
                updatedAt = now
            )
        }
    }

    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expireAt)

    fun cancel(): OrderReservation {
        if (status != ReservationStatus.PENDING)
            throw InvalidOrderStatusException(id, status.name, "PENDING")
        return copy(status = ReservationStatus.CANCELLED, updatedAt = LocalDateTime.now())
    }

    fun expire(): OrderReservation {
        if (status != ReservationStatus.PENDING)
            throw InvalidOrderStatusException(id, status.name, "PENDING")
        return copy(status = ReservationStatus.EXPIRED, updatedAt = LocalDateTime.now())
    }
}
