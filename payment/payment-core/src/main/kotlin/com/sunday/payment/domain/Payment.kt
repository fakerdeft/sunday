package com.sunday.payment.domain

import com.sunday.payment.exception.InvalidIdempotencyKeyException
import com.sunday.payment.exception.InvalidPaymentAmountException
import com.sunday.payment.exception.PaymentNotCompletableException
import com.sunday.payment.exception.PaymentNotFailableException
import com.sunday.payment.exception.PaymentNotRefundableException
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 결제 도메인 모델
 *
 * - 도메인이 스스로 상태 전이 검증
 * - Service에서 중복 검증 불필요
 */
data class Payment(
    val id: Long,
    val orderId: Long,
    val memberId: Long,
    val amount: BigDecimal,
    val status: PaymentStatus,
    val idempotencyKey: String,
    val failureReason: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        if (amount <= BigDecimal.ZERO) {
            throw InvalidPaymentAmountException(amount)
        }

        if (idempotencyKey.isBlank()) {
            throw InvalidIdempotencyKeyException()
        }
    }

    companion object {
        fun create(
            orderId: Long,
            memberId: Long,
            amount: BigDecimal,
            idempotencyKey: String
        ): Payment {
            return Payment(
                id = 0L,
                orderId = orderId,
                memberId = memberId,
                amount = amount,
                status = PaymentStatus.PROCESSING,
                idempotencyKey = idempotencyKey
            )
        }
    }

    fun complete(): Payment {
        if (status != PaymentStatus.PROCESSING) {
            throw PaymentNotCompletableException(id, status.name)
        }

        return copy(
            status = PaymentStatus.COMPLETED,
            updatedAt = LocalDateTime.now()
        )
    }

    fun fail(reason: String): Payment {
        if (status != PaymentStatus.PROCESSING) {
            throw PaymentNotFailableException(id, status.name)
        }

        return copy(
            status = PaymentStatus.FAILED,
            failureReason = reason,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 환불 처리
     *
     * @throws PaymentNotRefundableException COMPLETED 상태가 아닌 경우
     */
    fun refund(): Payment {
        if (status != PaymentStatus.COMPLETED) {
            throw PaymentNotRefundableException(id, status.name)
        }

        return copy(
            status = PaymentStatus.REFUNDED,
            updatedAt = LocalDateTime.now()
        )
    }
}
