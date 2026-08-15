package com.sunday.gate.client

/**
 * 주문 서버가 알려 주는 재고 현황이다.
 *
 * 게이트는 이 값에서 아직 주문하지 않은 통행증 수를 빼 발급 가능 수량을 정한다.
 * 재고의 원본은 주문 서버이고 여기 값은 주기마다 다시 읽는 참고값이다.
 */
data class ProductStockSnapshot(
    val productId: Long,

    /** 지금 바로 선점할 수 있는 재고 */
    val availableStock: Long
)
