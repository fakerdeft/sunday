package com.sunday.order.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class Order(
    val reservationId: Long,
    val memberId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalAmount: BigDecimal,
    val status: OrderStatus = OrderStatus.PAID,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        if (quantity <= 0) throw InvalidOrderQuantityException(quantity)
        if (unitPrice <= BigDecimal.ZERO) throw InvalidProductPriceException(unitPrice)
    }

    companion object {
        fun from(reservation: OrderReservation): Order = Order(
            reservationId = reservation.id,
            memberId = reservation.memberId,
            productId = reservation.productId,
            productName = reservation.productName,
            quantity = reservation.quantity,
            unitPrice = reservation.unitPrice,
            totalAmount = reservation.totalAmount,
            status = OrderStatus.PAID,
            createdAt = LocalDateTime.now()
        )
    }

    fun cancel(): Order {
        if (status == OrderStatus.CANCELLED) {

            return this
        }

        return copy(status = OrderStatus.CANCELLED)
    }
}
