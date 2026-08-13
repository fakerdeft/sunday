package com.sunday.order.domain

import java.time.LocalDateTime

data class ProductStock(
    val id: Long,
    val productId: Long,
    val status: StockStatus,
    val version: Long,
    val reservedBy: Long?,
    val reservationId: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class StockStatus {
    AVAILABLE,
    RESERVED,
    SOLD
}
