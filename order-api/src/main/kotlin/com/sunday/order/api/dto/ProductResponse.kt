package com.sunday.order.api.dto

import com.sunday.order.domain.Product
import java.math.BigDecimal

data class ProductResponse(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val stock: Long,
    val isHotDeal: Boolean,
    val hotDealActive: Boolean,
    val hotDealStartTime: String?,
    val hotDealEndTime: String?
) {
    companion object {
        fun from(product: Product, availableStock: Long): ProductResponse = ProductResponse(
            id = product.id,
            name = product.name,
            price = product.price,
            stock = availableStock,
            isHotDeal = product.isHotDeal,
            hotDealActive = product.isHotDealActive(),
            hotDealStartTime = product.hotDealStartTime?.toString(),
            hotDealEndTime = product.hotDealEndTime?.toString()
        )
    }
}
