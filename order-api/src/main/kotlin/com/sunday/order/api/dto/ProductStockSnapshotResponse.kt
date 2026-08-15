package com.sunday.order.api.dto

import com.sunday.order.application.ProductStockSnapshot

/**
 * 게이트 서버가 발급 가능 수량을 정할 때 쓰는 재고 현황이다.
 *
 * 결제 실패나 예약 만료로 재고가 돌아오면 이 값이 다시 올라가고, 게이트는 그때부터 통행증을 재개한다.
 */
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
