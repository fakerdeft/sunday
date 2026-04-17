package com.sunday.payment.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.sunday.support.infra.lock.DistributedLock
import com.sunday.payment.client.AccountApiClient
import com.sunday.payment.client.OrderApiClient
import com.sunday.payment.domain.Payment
import com.sunday.payment.domain.PaymentStatus
import com.sunday.payment.domain.exception.DuplicatePaymentException
import com.sunday.payment.domain.exception.OrderNotPayableForPaymentException
import com.sunday.payment.domain.exception.PaymentAlreadyCompletedException
import com.sunday.payment.domain.exception.PaymentNotFoundException
import com.sunday.payment.domain.exception.PaymentNotFoundByOrderException
import com.sunday.payment.domain.exception.PaymentProcessFailedException
import com.sunday.payment.repository.OutboxAggregateType
import com.sunday.payment.repository.OutboxEvent
import com.sunday.payment.repository.OutboxEventType
import com.sunday.payment.repository.OutboxRepository
import com.sunday.payment.repository.PaymentCompletedPayload
import com.sunday.payment.repository.PaymentRefundedPayload
import com.sunday.payment.repository.PaymentRepository
import com.sunday.payment.repository.RedisPaymentRepository
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val redisPaymentRepository: RedisPaymentRepository,
    private val accountApiClient: AccountApiClient,
    private val orderApiClient: OrderApiClient,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper
) {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    private val self: PaymentService get() = applicationContext.getBean(PaymentService::class.java)

    private val log = LogManager.getLogger(javaClass)

    companion object {
        private const val IDEMPOTENCY_TTL_SECONDS = 86400L
    }

    @DistributedLock(key = "'payment:order:' + #reservationId", waitTime = 3, leaseTime = 30)
    fun processPayment(reservationId: Long, memberId: Long, idempotencyKey: String): Payment {
        // 1. 멱등성 체크
        val existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)
        if (existingPayment != null) {
            log.info("기존 결제 반환: idempotencyKey=$idempotencyKey")
            return existingPayment
        }

        // 2. 멱등성 키 등록 (동시 요청 방지)
        if (!redisPaymentRepository.registerIdempotencyKey(idempotencyKey, IDEMPOTENCY_TTL_SECONDS)) {
            val payment = paymentRepository.findByIdempotencyKey(idempotencyKey)
            if (payment != null) return payment
            throw DuplicatePaymentException(idempotencyKey)
        }

        // 3. 선점 검증
        val reservationInfo = orderApiClient.getReservationInfo(reservationId)

        if (reservationInfo.memberId != memberId) {
            throw OrderNotPayableForPaymentException(reservationId, "해당 회원의 선점이 아닙니다")
        }

        if (!reservationInfo.canPay()) {
            throw OrderNotPayableForPaymentException(
                reservationId,
                "선점 상태: ${reservationInfo.status}, 만료 여부: ${reservationInfo.isExpired}"
            )
        }

        val existingOrderPayment = paymentRepository.findByOrderId(reservationId)
        if (existingOrderPayment != null && existingOrderPayment.status == PaymentStatus.COMPLETED) {
            throw PaymentAlreadyCompletedException(reservationId)
        }

        val payment = paymentRepository.save(
            Payment.create(
                orderId = reservationId,
                memberId = memberId,
                amount = reservationInfo.totalAmount,
                idempotencyKey = idempotencyKey
            )
        )

        var accountCharged = false
        try {
            // 4. 잔액 차감
            accountApiClient.withdraw(
                memberId = memberId,
                amount = reservationInfo.totalAmount,
                description = "주문 결제 (선점번호: $reservationId)"
            )
            accountCharged = true

            // 5. 확정 주문 생성 (선점 → PAID Order)
            orderApiClient.confirmReservation(reservationId)

            // 6. 결제 완료 + Outbox 원자적 저장
            return self.completePaymentWithOutbox(payment, reservationId, memberId, reservationInfo.totalAmount)

        } catch (e: Exception) {
            log.error("결제 실패: reservationId=$reservationId", e)

            if (accountCharged) {
                try {
                    accountApiClient.deposit(
                        memberId = memberId,
                        amount = reservationInfo.totalAmount,
                        description = "결제 실패 환불 (선점번호: $reservationId)"
                    )
                    log.info("보상 트랜잭션 완료: reservationId=$reservationId, 잔액 복구")
                } catch (ce: Exception) {
                    log.error("보상 트랜잭션(잔액 복구) 실패: reservationId=$reservationId, memberId=$memberId", ce)
                }
            }

            try {
                orderApiClient.cancelReservation(reservationId)
                log.info("선점 취소 완료: reservationId=$reservationId, 재고 복구")
            } catch (ce: Exception) {
                log.error("선점 취소 실패 (만료 스케줄러가 처리 예정): reservationId=$reservationId", ce)
            }

            paymentRepository.save(payment.fail(e.message ?: "알 수 없는 오류"))
            throw PaymentProcessFailedException(reservationId, e.message ?: "알 수 없는 오류")
        }
    }

    @Transactional
    fun completePaymentWithOutbox(payment: Payment, reservationId: Long, memberId: Long, amount: BigDecimal): Payment {
        val savedPayment = paymentRepository.save(payment.complete())
        savePaymentCompletedEvent(savedPayment.id, reservationId, memberId, amount.toString())
        return savedPayment
    }

    @Transactional(readOnly = true)
    fun getPayment(paymentId: Long): Payment =
        paymentRepository.findById(paymentId) ?: throw PaymentNotFoundException(paymentId)

    @Transactional(readOnly = true)
    fun getPaymentByOrderId(reservationId: Long): Payment =
        paymentRepository.findByOrderId(reservationId) ?: throw PaymentNotFoundByOrderException(reservationId)

    @Transactional(readOnly = true)
    fun getMyPayments(memberId: Long): List<Payment> = paymentRepository.findByMemberId(memberId)

    fun refundPayment(paymentId: Long): Payment {
        val payment = getPayment(paymentId).refund()

        // 1. 잔액 복구
        accountApiClient.deposit(
            memberId = payment.memberId,
            amount = payment.amount,
            description = "주문 환불 (선점번호: ${payment.orderId})"
        )

        var orderCancelled = false
        try {
            // 2. 확정 주문 취소 (재고 복구 X)
            orderApiClient.cancelOrder(payment.orderId)
            orderCancelled = true

            // 3. 결제 기록 저장 + Outbox 원자적 저장
            return self.saveRefundWithOutbox(payment)

        } catch (e: Exception) {
            log.error("환불 처리 실패: paymentId=$paymentId", e)

            if (!orderCancelled) {
                try {
                    accountApiClient.withdraw(
                        memberId = payment.memberId,
                        amount = payment.amount,
                        description = "환불 실패 재차감 (선점번호: ${payment.orderId})"
                    )
                    log.info("보상 트랜잭션 완료: paymentId=$paymentId, 잔액 재차감")
                } catch (ce: Exception) {
                    log.error("보상 트랜잭션(잔액 재차감) 실패: paymentId=$paymentId", ce)
                }
            }

            throw e
        }
    }

    @Transactional
    fun saveRefundWithOutbox(payment: Payment): Payment {
        val savedPayment = paymentRepository.save(payment)
        savePaymentRefundedEvent(savedPayment.id, payment.orderId, payment.memberId, payment.amount.toString())
        return savedPayment
    }

    private fun savePaymentCompletedEvent(paymentId: Long, reservationId: Long, memberId: Long, amount: String) {
        val payload = objectMapper.writeValueAsString(
            PaymentCompletedPayload(paymentId = paymentId, orderId = reservationId, memberId = memberId, amount = amount)
        )
        outboxRepository.save(
            OutboxEvent.create(
                aggregateType = OutboxAggregateType.PAYMENT,
                aggregateId = paymentId,
                eventType = OutboxEventType.PAYMENT_COMPLETED,
                payload = payload
            )
        )
    }

    private fun savePaymentRefundedEvent(paymentId: Long, reservationId: Long, memberId: Long, amount: String) {
        val payload = objectMapper.writeValueAsString(
            PaymentRefundedPayload(paymentId = paymentId, orderId = reservationId, memberId = memberId, amount = amount)
        )
        outboxRepository.save(
            OutboxEvent.create(
                aggregateType = OutboxAggregateType.PAYMENT,
                aggregateId = paymentId,
                eventType = OutboxEventType.PAYMENT_REFUNDED,
                payload = payload
            )
        )
    }
}
