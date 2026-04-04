package com.sunday.order.repository

data class StockReservation(
    val productId: Long,
    val memberId: Long,
    val quantity: Int
)
