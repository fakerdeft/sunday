package com.sunday.gate.client

/** 재고 원본은 주문 서버. 여기 값은 주기마다 다시 읽는 참고값이다. */
data class ProductStockSnapshot(
    val productId: Long,

    val availableStock: Long
)
