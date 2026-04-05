package com.sunday.payment.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.sunday.support.infra.lock.DistributedLock
import com.sunday.payment.client.AccountClient
import com.sunday.payment.client.OrderClient
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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val redisPaymentRepository: RedisPaymentRepository,
    private val accountClient: AccountClient,
    private val orderClient: OrderClient,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper
) {
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
        val orderInfo = orderClient.getOrderInfo(orderId)

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
        var payment = Payment.create(
            orderId = orderId,
            memberId = memberId,
            amount = orderInfo.totalAmount,
            idempotencyKey = idempotencyKey
        )
        payment = paymentRepository.save(payment)

        try {
            // 4. 잔액 차감
            accountClient.withdraw(
                memberId = memberId,
                amount = orderInfo.totalAmount,
                description = "주문 결제 (주문번호: $orderId)"
            )

            // 5. 주문 상태 변경
            orderClient.markOrderAsPaid(orderId)

            // 6. 결제 완료
            payment = payment.complete()
            val savedPayment = paymentRepository.save(payment)

            // 7. Outbox 이벤트 저장 (동일 트랜잭션)
            savePaymentCompletedEvent(savedPayment.id, orderId, memberId, orderInfo.totalAmount.toString())

            return savedPayment

        } catch (e: Exception) {
            log.error("결제 실패: orderId=$orderId", e)
            payment = payment.fail(e.message ?: "알 수 없는 오류")
            paymentRepository.save(payment)
            throw PaymentProcessFailedException(orderId, e.message ?: "알 수 없는 오류")
        }
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

    @Transactional
    fun refundPayment(paymentId: Long): Payment {
        var payment = getPayment(paymentId)

        payment = payment.refund()

        // 1. 잔액 복구
        accountClient.deposit(
            memberId = payment.memberId,
            amount = payment.amount,
            description = "주문 환불 (주문번호: ${payment.orderId})"
        )

        // 2. 주문 취소 (재고 복구 포함)
        orderClient.cancelOrder(payment.orderId)

        // 3. 결제 기록 저장
        val savedPayment = paymentRepository.save(payment)

        // 4. Outbox 이벤트 저장
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
