package com.sunday.payment.domain

import com.sunday.payment.domain.exception.InvalidIdempotencyKeyException
import com.sunday.payment.domain.exception.InvalidPaymentAmountException
import com.sunday.payment.domain.exception.PaymentNotCompletableException
import com.sunday.payment.domain.exception.PaymentNotFailableException
import com.sunday.payment.domain.exception.PaymentNotRefundableException
import java.math.BigDecimal
import java.time.LocalDateTime

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
        if (amount <= BigDecimal.ZERO) throw InvalidPaymentAmountException(amount)
        if (idempotencyKey.isBlank()) throw InvalidIdempotencyKeyException()
    }

    companion object {
        fun create(
            orderId: Long,
            memberId: Long,
            amount: BigDecimal,
            idempotencyKey: String
        ): Payment = Payment(
            id = 0L,
            orderId = orderId,
            memberId = memberId,
            amount = amount,
            status = PaymentStatus.PROCESSING,
            idempotencyKey = idempotencyKey
        )
    }

    fun markAccountDebited(): Payment {
        if (status != PaymentStatus.PROCESSING) throw PaymentNotCompletableException(id, status.name)

        return copy(status = PaymentStatus.ACCOUNT_DEBITED, updatedAt = LocalDateTime.now())
    }

    fun markOrderConfirmed(): Payment {
        if (status != PaymentStatus.ACCOUNT_DEBITED) throw PaymentNotCompletableException(id, status.name)

        return copy(status = PaymentStatus.ORDER_CONFIRMED, updatedAt = LocalDateTime.now())
    }

    fun complete(): Payment {
        if (status != PaymentStatus.ORDER_CONFIRMED) throw PaymentNotCompletableException(id, status.name)

        return copy(status = PaymentStatus.COMPLETED, updatedAt = LocalDateTime.now())
    }

    fun fail(reason: String): Payment {
        if (status !in setOf(PaymentStatus.PROCESSING, PaymentStatus.ACCOUNT_DEBITED))
            throw PaymentNotFailableException(id, status.name)

        return copy(status = PaymentStatus.FAILED, failureReason = reason, updatedAt = LocalDateTime.now())
    }

    fun startRefund(): Payment {
        if (status != PaymentStatus.COMPLETED) throw PaymentNotRefundableException(id, status.name)

        return copy(status = PaymentStatus.REFUND_PROCESSING, updatedAt = LocalDateTime.now())
    }

    fun refund(): Payment {
        if (status != PaymentStatus.REFUND_PROCESSING) throw PaymentNotRefundableException(id, status.name)

        return copy(status = PaymentStatus.REFUNDED, updatedAt = LocalDateTime.now())
    }
}
