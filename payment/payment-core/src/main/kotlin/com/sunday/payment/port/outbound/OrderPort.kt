package com.sunday.payment.port.outbound

import java.math.BigDecimal

/**
 * Order 도메인 연동 포트
 */
interface OrderPort {
    /**
     * 주문 정보 조회
     */
    fun getOrderInfo(orderId: Long): OrderInfo

    /**
     * 주문을 결제 완료 상태로 변경
     */
    fun markOrderAsPaid(orderId: Long)

    /**
     * 주문 취소 (환불 시)
     */
    fun cancelOrder(orderId: Long)
}

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
