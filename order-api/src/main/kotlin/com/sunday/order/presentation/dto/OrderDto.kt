package com.sunday.order.presentation.dto

import com.sunday.order.domain.Order
import com.sunday.order.domain.Product
import java.math.BigDecimal

data class CreateOrderRequest(
    val productId: Long,
    val quantity: Int = 1
)

data class AsyncOrderResponse(
    val status: String = "PROCESSING",
    val reservationKey: String,
    val message: String = "주문이 접수되었습니다. 잠시 후 주문 내역을 확인해주세요."
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
        fun from(product: Product, currentStock: Int): ProductResponse = ProductResponse(
            id = product.id,
            name = product.name,
            price = product.price,
            stock = currentStock,
            isHotDeal = product.isHotDeal,
            hotDealActive = product.isHotDealActive(),
            hotDealStartTime = product.hotDealStartTime?.toString(),
            hotDealEndTime = product.hotDealEndTime?.toString()
        )
    }
}

data class OrderResponse(
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
        fun from(order: Order): OrderResponse = OrderResponse(
            id = order.id,
            memberId = order.memberId,
            productId = order.productId,
            productName = order.productName,
            quantity = order.quantity,
            unitPrice = order.unitPrice,
            totalAmount = order.totalAmount,
            status = order.status.name,
            expireAt = order.expireAt.toString(),
            createdAt = order.createdAt.toString()
        )
    }
}
