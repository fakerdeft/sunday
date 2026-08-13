package com.sunday.payment.application

import com.sunday.payment.domain.Payment
import com.sunday.payment.domain.PaymentStatus
import com.sunday.payment.domain.exception.PaymentNotCompletableException
import com.sunday.payment.domain.exception.PaymentNotFoundByOrderException
import com.sunday.payment.domain.exception.PaymentNotFoundException
import com.sunday.payment.domain.exception.PaymentNotRefundableException
import com.sunday.payment.repository.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Payment DB에 대한 짧은 로컬 트랜잭션만 담당한다.
 * 서비스 간 HTTP 호출은 이 클래스 밖의 PaymentService에서 수행한다.
 */
@Service
class PaymentTransactionService(
    private val paymentRepository: PaymentRepository
) {
    @Transactional(readOnly = true)
    fun findByIdempotencyKey(idempotencyKey: String): Payment? =
        paymentRepository.findByIdempotencyKey(idempotencyKey)

    @Transactional(readOnly = true)
    fun findByOrderId(orderId: Long): Payment? = paymentRepository.findByOrderId(orderId)

    @Transactional
    fun createProcessingPayment(
        reservationId: Long,
        memberId: Long,
        amount: BigDecimal,
        idempotencyKey: String
    ): Payment = paymentRepository.saveAndFlush(
        Payment.create(
            orderId = reservationId,
            memberId = memberId,
            amount = amount,
            idempotencyKey = idempotencyKey
        )
    )

    @Transactional
    fun markAccountDebited(paymentId: Long): Payment {
        val payment = findByIdForUpdate(paymentId)

        return when (payment.status) {
            PaymentStatus.PROCESSING -> paymentRepository.save(payment.markAccountDebited())
            PaymentStatus.ACCOUNT_DEBITED,
            PaymentStatus.ORDER_CONFIRMED,
            PaymentStatus.COMPLETED -> payment

            else -> throw PaymentNotCompletableException(payment.id, payment.status.name)
        }
    }

    @Transactional
    fun markOrderConfirmed(paymentId: Long): Payment {
        val payment = findByIdForUpdate(paymentId)

        return when (payment.status) {
            PaymentStatus.ACCOUNT_DEBITED -> paymentRepository.save(payment.markOrderConfirmed())
            PaymentStatus.ORDER_CONFIRMED,
            PaymentStatus.COMPLETED -> payment
            else -> throw PaymentNotCompletableException(payment.id, payment.status.name)
        }
    }

    @Transactional
    fun completePayment(paymentId: Long): Payment {
        val payment = findByIdForUpdate(paymentId)

        if (payment.status == PaymentStatus.COMPLETED) {

            return payment
        }

        return paymentRepository.save(payment.complete())
    }

    @Transactional
    fun failPayment(paymentId: Long, reason: String): Payment {
        val payment = findByIdForUpdate(paymentId)

        if (payment.status == PaymentStatus.FAILED) {

            return payment
        }

        return paymentRepository.save(payment.fail(reason))
    }

    @Transactional(readOnly = true)
    fun getPayment(paymentId: Long): Payment =
        paymentRepository.findById(paymentId) ?: throw PaymentNotFoundException(paymentId)

    @Transactional(readOnly = true)
    fun getPaymentByOrderId(reservationId: Long): Payment =
        paymentRepository.findByOrderId(reservationId) ?: throw PaymentNotFoundByOrderException(reservationId)

    @Transactional(readOnly = true)
    fun getMyPayments(memberId: Long): List<Payment> = paymentRepository.findByMemberId(memberId)

    @Transactional
    fun startRefund(paymentId: Long, memberId: Long): Payment {
        val payment = findByIdForUpdate(paymentId)

        if (payment.memberId != memberId) {
            throw PaymentNotFoundException(paymentId)
        }

        return when (payment.status) {
            PaymentStatus.REFUNDED,
            PaymentStatus.REFUND_PROCESSING -> payment
            PaymentStatus.COMPLETED -> paymentRepository.save(payment.startRefund())
            else -> throw PaymentNotRefundableException(payment.id, payment.status.name)
        }
    }

    @Transactional
    fun completeRefund(paymentId: Long): Payment {
        val payment = findByIdForUpdate(paymentId)

        if (payment.status == PaymentStatus.REFUNDED) {

            return payment
        }

        return paymentRepository.save(payment.refund())
    }

    private fun findByIdForUpdate(paymentId: Long): Payment =
        paymentRepository.findByIdForUpdate(paymentId) ?: throw PaymentNotFoundException(paymentId)
}
