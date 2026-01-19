package com.sunday.payment.adapter.inbound.dto

import com.sunday.payment.domain.Payment
import java.math.BigDecimal

// ===== Request DTOs =====

data class ProcessPaymentRequest(
    val orderId: Long,
    val idempotencyKey: String
)

// ===== Response DTOs =====

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
        fun from(payment: Payment): PaymentResponse {
            return PaymentResponse(
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
}
