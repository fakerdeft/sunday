package com.sunday.order.application

data class ProductStockSnapshot(
    val productId: Long,

    /** 지금 바로 선점할 수 있는 재고 */
    val availableStock: Long
)
