package com.sunday.gate.application

import java.time.Instant

enum class OrderPassStatus {
    PASSED,

    SOLD_OUT
}

data class OrderPass(
    val productId: Long,
    val memberId: Long,
    val status: OrderPassStatus,

    val token: String?,

    val expiresAt: Instant?
) {
    fun canOrder(): Boolean = status == OrderPassStatus.PASSED
}
