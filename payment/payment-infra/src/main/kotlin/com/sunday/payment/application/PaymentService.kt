package com.sunday.payment.application

import com.sunday.payment.domain.Payment
import com.sunday.payment.domain.PaymentStatus
import com.sunday.payment.exception.DuplicatePaymentException
import com.sunday.payment.exception.OrderNotPayableException
import com.sunday.payment.exception.PaymentAlreadyCompletedException
import com.sunday.payment.exception.PaymentFailedException
import com.sunday.payment.exception.PaymentLockAcquisitionException
import com.sunday.payment.exception.PaymentNotFoundByOrderException
import com.sunday.payment.exception.PaymentNotFoundException
import com.sunday.payment.port.inbound.PaymentUseCase
import com.sunday.payment.port.outbound.AccountPort
import com.sunday.payment.port.outbound.OrderPort
import com.sunday.payment.port.outbound.PaymentLockRepository
import com.sunday.payment.port.outbound.PaymentRepository
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentLockRepository: PaymentLockRepository,
    private val accountPort: AccountPort,
    private val orderPort: OrderPort
) : PaymentUseCase {

    private val log = LogManager.getLogger(javaClass)

    companion object {
        private const val LOCK_TTL_SECONDS = 30L
        private const val IDEMPOTENCY_TTL_SECONDS = 86400L // 24시간
    }

    /**
     * 결제 처리
     *
     * 1. 멱등성 키로 기존 결제 확인 (있으면 기존 결과 반환)
     * 2. 분산 락 획득
     * 3. 주문 검증 (상태, 만료, 소유자)
     * 4. 잔액 차감
     * 5. 주문 상태 변경
     * 6. 결제 기록 저장
     */
    @Transactional
    override fun processPayment(orderId: Long, memberId: Long, idempotencyKey: String): Payment {
        // 1. 멱등성 체크 - 이미 처리된 요청이면 기존 결과 반환
        val existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)

        if (existingPayment != null) {
            log.info("Returning existing payment for idempotency key: $idempotencyKey")
            return existingPayment
        }

        // 2. 분산 락 획득
        if (!paymentLockRepository.acquireLock(orderId, LOCK_TTL_SECONDS)) {
            throw PaymentLockAcquisitionException(orderId)
        }

        try {
            // 멱등성 키 등록 (동시 요청 방지)
            if (!paymentLockRepository.registerIdempotencyKey(idempotencyKey, IDEMPOTENCY_TTL_SECONDS)) {
                // 다른 요청이 먼저 등록했으면 해당 결과 대기 후 반환
                val payment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                if (payment != null) return payment
                throw DuplicatePaymentException(idempotencyKey)
            }

            // 3. 주문 검증
            val orderInfo = orderPort.getOrderInfo(orderId)

            if (orderInfo.memberId != memberId) {
                throw OrderNotPayableException(orderId, "Order does not belong to this member")
            }

            if (!orderInfo.canPay()) {
                throw OrderNotPayableException(
                    orderId,
                    "Order status: ${orderInfo.status}, Expired: ${orderInfo.isExpired}"
                )
            }

            // 이미 결제된 주문인지 확인
            val existingOrderPayment = paymentRepository.findByOrderId(orderId)
            if (existingOrderPayment != null && existingOrderPayment.status == PaymentStatus.COMPLETED) {
                throw PaymentAlreadyCompletedException(orderId)
            }

            // 결제 기록 생성 (PROCESSING)
            var payment = Payment.create(
                orderId = orderId,
                memberId = memberId,
                amount = orderInfo.totalAmount,
                idempotencyKey = idempotencyKey
            )
            payment = paymentRepository.save(payment)

            try {
                // 4. 잔액 차감
                accountPort.withdraw(
                    memberId = memberId,
                    amount = orderInfo.totalAmount,
                    description = "주문 결제 (주문번호: $orderId)"
                )

                // 5. 주문 상태 변경
                orderPort.markOrderAsPaid(orderId)

                // 6. 결제 완료
                payment = payment.complete()

                return paymentRepository.save(payment)

            } catch (e: Exception) {
                // 결제 실패 처리
                log.error("Payment failed for order $orderId", e)
                payment = payment.fail(e.message ?: "Unknown error")
                paymentRepository.save(payment)
                throw PaymentFailedException(orderId, e.message ?: "Unknown error")
            }

        } finally {
            // 락 해제
            paymentLockRepository.releaseLock(orderId)
        }
    }

    @Transactional(readOnly = true)
    override fun getPayment(paymentId: Long): Payment {
        return paymentRepository.findById(paymentId)
            ?: throw PaymentNotFoundException(paymentId)
    }

    @Transactional(readOnly = true)
    override fun getPaymentByOrderId(orderId: Long): Payment {
        return paymentRepository.findByOrderId(orderId)
            ?: throw PaymentNotFoundByOrderException(orderId)
    }

    @Transactional(readOnly = true)
    override fun getMyPayments(memberId: Long): List<Payment> {
        return paymentRepository.findByMemberId(memberId)
    }

    /**
     * 환불 처리
     */
    @Transactional
    override fun refundPayment(paymentId: Long): Payment {
        var payment = getPayment(paymentId)

        // 환불 처리
        payment = payment.refund()

        // 1. 잔액 복구
        accountPort.deposit(
            memberId = payment.memberId,
            amount = payment.amount,
            description = "주문 환불 (주문번호: ${payment.orderId})"
        )

        // 2. 주문 취소 (재고 복구 포함)
        orderPort.cancelOrder(payment.orderId)

        // 3. 결제 기록 저장
        return paymentRepository.save(payment)
    }
}
