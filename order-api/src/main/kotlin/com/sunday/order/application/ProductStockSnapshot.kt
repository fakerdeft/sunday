package com.sunday.order.application

data class ProductStockSnapshot(
    val productId: Long,

    val availableStock: Long
)
