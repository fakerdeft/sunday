package com.sunday.order.api.dto

import com.sunday.order.application.ProductStockSnapshot

data class ProductStockSnapshotResponse(
    val productId: Long,
    val availableStock: Long
) {
    companion object {
        fun from(snapshot: ProductStockSnapshot): ProductStockSnapshotResponse =
            ProductStockSnapshotResponse(
                productId = snapshot.productId,
                availableStock = snapshot.availableStock
            )
    }
}
