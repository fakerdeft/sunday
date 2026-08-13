package com.sunday.order.api.dto

import com.sunday.order.domain.Order
import java.math.BigDecimal

data class OrderResponse(
    val reservationId: Long,
    val memberId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalAmount: BigDecimal,
    val status: String,
    val createdAt: String
) {
    companion object {
        fun from(order: Order): OrderResponse = OrderResponse(
            reservationId = order.reservationId,
            memberId = order.memberId,
            productId = order.productId,
            productName = order.productName,
            quantity = order.quantity,
            unitPrice = order.unitPrice,
            totalAmount = order.totalAmount,
            status = order.status.name,
            createdAt = order.createdAt.toString()
        )
    }
}
