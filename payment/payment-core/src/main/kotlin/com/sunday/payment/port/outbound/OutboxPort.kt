package com.sunday.payment.port.outbound

interface OutboxPort {
    fun savePaymentCompletedEvent(
        paymentId: Long,
        orderId: Long,
        memberId: Long,
        amount: String
    )

    fun savePaymentRefundedEvent(
        paymentId: Long,
        orderId: Long,
        memberId: Long,
        amount: String
    )
}
