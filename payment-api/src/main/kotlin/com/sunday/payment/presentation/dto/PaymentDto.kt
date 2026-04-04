package com.sunday.payment.presentation.dto

import com.sunday.payment.domain.Payment
import java.math.BigDecimal

data class ProcessPaymentRequest(
    val orderId: Long,
    val idempotencyKey: String
)

data class PaymentResponse(
    val id: Long,
    val orderId: Long,
    val memberId: Long,
    val amount: BigDecimal,
    val status: String,
    val idempotencyKey: String,
    val failureReason: String?,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(payment: Payment): PaymentResponse = PaymentResponse(
            id = payment.id,
            orderId = payment.orderId,
            memberId = payment.memberId,
            amount = payment.amount,
            status = payment.status.name,
            idempotencyKey = payment.idempotencyKey,
            failureReason = payment.failureReason,
            createdAt = payment.createdAt.toString(),
            updatedAt = payment.updatedAt.toString()
        )
    }
}
