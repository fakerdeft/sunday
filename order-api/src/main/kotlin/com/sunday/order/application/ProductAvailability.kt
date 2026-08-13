package com.sunday.order.application

import com.sunday.order.domain.Product

data class ProductAvailability(
    val product: Product,
    val availableStock: Long
)
