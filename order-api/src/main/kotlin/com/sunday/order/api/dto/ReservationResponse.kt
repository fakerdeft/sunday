package com.sunday.order.api.dto

import com.sunday.order.domain.OrderReservation
import java.math.BigDecimal

data class ReservationResponse(
    val id: Long,
    val memberId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalAmount: BigDecimal,
    val status: String,
    val expireAt: String,
    val createdAt: String
) {
    companion object {
        fun from(r: OrderReservation): ReservationResponse = ReservationResponse(
            id = r.id,
            memberId = r.memberId,
            productId = r.productId,
            productName = r.productName,
            quantity = r.quantity,
            unitPrice = r.unitPrice,
            totalAmount = r.totalAmount,
            status = r.status.name,
            expireAt = r.expireAt.toString(),
            createdAt = r.createdAt.toString()
        )
    }
}
