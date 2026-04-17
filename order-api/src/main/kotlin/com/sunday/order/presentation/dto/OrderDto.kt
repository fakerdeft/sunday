package com.sunday.order.presentation.dto

import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.Product
import java.math.BigDecimal

data class CreateOrderRequest(
    val productId: Long,
    val quantity: Int = 1
)

data class ProductResponse(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val stock: Int,
    val isHotDeal: Boolean,
    val hotDealActive: Boolean,
    val hotDealStartTime: String?,
    val hotDealEndTime: String?
) {
    companion object {
        fun from(product: Product): ProductResponse = ProductResponse(
            id = product.id,
            name = product.name,
            price = product.price,
            stock = product.stock,
            isHotDeal = product.isHotDeal,
            hotDealActive = product.isHotDealActive(),
            hotDealStartTime = product.hotDealStartTime?.toString(),
            hotDealEndTime = product.hotDealEndTime?.toString()
        )
    }
}

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

data class OrderResponse(
    val reservationId: Long,
    val memberId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalAmount: BigDecimal,
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
            createdAt = order.createdAt.toString()
        )
    }
}
