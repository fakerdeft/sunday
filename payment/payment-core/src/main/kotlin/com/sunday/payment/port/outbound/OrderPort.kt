package com.sunday.payment.port.outbound

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
