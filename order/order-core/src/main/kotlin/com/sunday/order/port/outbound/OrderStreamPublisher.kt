package com.sunday.order.port.outbound

/**
 * Order Stream Publisher (Output Port)
 */
interface OrderStreamPublisher {
    /**
     * 주문 생성 이벤트 발행
     * @return 메시지 ID
     */
    fun publishOrderCreated(
        reservationKey: String,
        memberId: Long,
        productId: Long,
        productName: String,
        quantity: Int,
        unitPrice: String,
        totalAmount: String
    ): String
}
