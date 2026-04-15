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

    @DistributedLock(key = "'payment:order:' + #orderId", waitTime = 3, leaseTime = 30)
    fun processPayment(orderId: Long, memberId: Long, idempotencyKey: String): Payment {
        // 1. 멱등성 체크 - 이미 처리된 요청이면 기존 결과 반환
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

        // 3. 주문 검증
        val orderInfo = orderApiClient.getOrderInfo(orderId)

        if (orderInfo.memberId != memberId) {
            throw OrderNotPayableForPaymentException(orderId, "해당 회원의 주문이 아닙니다")
        }

        if (!orderInfo.canPay()) {
            throw OrderNotPayableForPaymentException(
                orderId,
                "주문 상태: ${orderInfo.status}, 만료 여부: ${orderInfo.isExpired}"
            )
        }

        // 이미 결제된 주문인지 확인
        val existingOrderPayment = paymentRepository.findByOrderId(orderId)
        if (existingOrderPayment != null && existingOrderPayment.status == PaymentStatus.COMPLETED) {
            throw PaymentAlreadyCompletedException(orderId)
        }

        // 결제 기록 생성 (PROCESSING)
        val payment = paymentRepository.save(
            Payment.create(
                orderId = orderId,
                memberId = memberId,
                amount = orderInfo.totalAmount,
                idempotencyKey = idempotencyKey
            )
        )

        var accountCharged = false
        try {
            // 4. 잔액 차감
            accountApiClient.withdraw(
                memberId = memberId,
                amount = orderInfo.totalAmount,
                description = "주문 결제 (주문번호: $orderId)"
            )
            accountCharged = true

            // 5. 주문 상태 변경
            orderApiClient.markOrderAsPaid(orderId)

            // 6. 결제 완료 + Outbox 원자적 저장
            return self.completePaymentWithOutbox(payment, orderId, memberId, orderInfo.totalAmount)

        } catch (e: Exception) {
            log.error("결제 실패: orderId=$orderId", e)

            // 보상 트랜잭션: 잔액이 이미 차감된 경우 복구
            if (accountCharged) {
                try {
                    accountApiClient.deposit(
                        memberId = memberId,
                        amount = orderInfo.totalAmount,
                        description = "결제 실패 환불 (주문번호: $orderId)"
                    )
                    log.info("보상 트랜잭션 완료: orderId=$orderId, 잔액 복구")
                } catch (ce: Exception) {
                    log.error("보상 트랜잭션(잔액 복구) 실패: orderId=$orderId, memberId=$memberId", ce)
                }
            }

            paymentRepository.save(payment.fail(e.message ?: "알 수 없는 오류"))
            throw PaymentProcessFailedException(orderId, e.message ?: "알 수 없는 오류")
        }
    }

    /**
     * 결제 완료 상태 저장 + Outbox 이벤트를 동일 트랜잭션에서 원자적으로 처리.
     * self-proxy 호출로 @Transactional AOP 적용.
     */
    @Transactional
    fun completePaymentWithOutbox(payment: Payment, orderId: Long, memberId: Long, amount: BigDecimal): Payment {
        val savedPayment = paymentRepository.save(payment.complete())
        savePaymentCompletedEvent(savedPayment.id, orderId, memberId, amount.toString())
        return savedPayment
    }

    @Transactional(readOnly = true)
    fun getPayment(paymentId: Long): Payment {
        return paymentRepository.findById(paymentId) ?: throw PaymentNotFoundException(paymentId)
    }

    @Transactional(readOnly = true)
    fun getPaymentByOrderId(orderId: Long): Payment {
        return paymentRepository.findByOrderId(orderId) ?: throw PaymentNotFoundByOrderException(orderId)
    }

    @Transactional(readOnly = true)
    fun getMyPayments(memberId: Long): List<Payment> {
        return paymentRepository.findByMemberId(memberId)
    }

    fun refundPayment(paymentId: Long): Payment {
        val payment = getPayment(paymentId).refund()

        // 1. 잔액 복구
        accountApiClient.deposit(
            memberId = payment.memberId,
            amount = payment.amount,
            description = "주문 환불 (주문번호: ${payment.orderId})"
        )

        var orderCancelled = false
        try {
            // 2. 주문 취소 (재고 복구 포함)
            orderApiClient.cancelOrder(payment.orderId)
            orderCancelled = true

            // 3. 결제 기록 저장 + Outbox 원자적 저장
            return self.saveRefundWithOutbox(payment)

        } catch (e: Exception) {
            log.error("환불 처리 실패: paymentId=$paymentId", e)

            // 보상 트랜잭션: 주문 취소 전에 실패했으면 잔액 재차감
            if (!orderCancelled) {
                try {
                    accountApiClient.withdraw(
                        memberId = payment.memberId,
                        amount = payment.amount,
                        description = "환불 실패 재차감 (주문번호: ${payment.orderId})"
                    )
                    log.info("보상 트랜잭션 완료: paymentId=$paymentId, 잔액 재차감")
                } catch (ce: Exception) {
                    log.error("보상 트랜잭션(잔액 재차감) 실패: paymentId=$paymentId", ce)
                }
            }

            throw e
        }
    }

    /**
     * 환불 상태 저장 + Outbox 이벤트를 동일 트랜잭션에서 원자적으로 처리.
     */
    @Transactional
    fun saveRefundWithOutbox(payment: Payment): Payment {
        val savedPayment = paymentRepository.save(payment)
        savePaymentRefundedEvent(savedPayment.id, payment.orderId, payment.memberId, payment.amount.toString())
        return savedPayment
    }

    private fun savePaymentCompletedEvent(paymentId: Long, orderId: Long, memberId: Long, amount: String) {
        val payload = objectMapper.writeValueAsString(
            PaymentCompletedPayload(paymentId = paymentId, orderId = orderId, memberId = memberId, amount = amount)
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

    private fun savePaymentRefundedEvent(paymentId: Long, orderId: Long, memberId: Long, amount: String) {
        val payload = objectMapper.writeValueAsString(
            PaymentRefundedPayload(paymentId = paymentId, orderId = orderId, memberId = memberId, amount = amount)
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
