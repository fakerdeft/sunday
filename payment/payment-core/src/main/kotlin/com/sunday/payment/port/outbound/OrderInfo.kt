package com.sunday.payment.port.outbound

import java.math.BigDecimal

/**
 * 주문 정보 (Payment에서 필요한 최소 정보)
 */
data class OrderInfo(
    val orderId: Long,
    val memberId: Long,
    val totalAmount: BigDecimal,
    val status: String,
    val isExpired: Boolean
) {
    fun canPay(): Boolean = status == "PENDING" && !isExpired
}
