package com.sunday.order.port.outbound

/**
 * 재고 선점 정보
 */
data class StockReservation(
    val productId: Long,
    val memberId: Long,
    val quantity: Int
)
