package com.sunday.gate.application

import java.time.Instant

enum class OrderPassStatus {
    /** 재고가 남아 있어 주문 서버로 통과 */
    PASSED,

    /** 재고가 없어 주문 서버까지 가지 않고 거절 */
    SOLD_OUT
}

data class OrderPass(
    val productId: Long,
    val memberId: Long,
    val status: OrderPassStatus,

    /** 통과한 경우 주문 서버에 제시할 증표 */
    val token: String?,

    /** 통행증을 반납해야 하는 시각 */
    val expiresAt: Instant?
) {
    fun canOrder(): Boolean = status == OrderPassStatus.PASSED
}
