package com.sunday.payment.repository.outbox.payload

data class PaymentCompletedPayload(
    val paymentId: Long,
    val orderId: Long,
    val memberId: Long,
    val amount: String
)

data class PaymentRefundedPayload(
    val paymentId: Long,
    val orderId: Long,
    val memberId: Long,
    val amount: String
)
