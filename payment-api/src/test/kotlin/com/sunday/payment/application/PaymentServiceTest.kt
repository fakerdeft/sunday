package com.sunday.payment.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.sunday.payment.client.AccountApiClient
import com.sunday.payment.client.OrderApiClient
import com.sunday.payment.client.ReservationInfo
import com.sunday.payment.domain.Payment
import com.sunday.payment.domain.PaymentStatus
import com.sunday.payment.domain.exception.DuplicatePaymentException
import com.sunday.payment.domain.exception.OrderNotPayableForPaymentException
import com.sunday.payment.domain.exception.PaymentAlreadyCompletedException
import com.sunday.payment.domain.exception.PaymentNotRefundableException
import com.sunday.payment.domain.exception.PaymentProcessFailedException
import com.sunday.payment.repository.OutboxEvent
import com.sunday.payment.repository.OutboxRepository
import com.sunday.payment.repository.PaymentRepository
import com.sunday.payment.repository.RedisPaymentRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext
import java.math.BigDecimal
import java.time.LocalDateTime

class PaymentServiceTest {

    private val paymentRepository = mockk<PaymentRepository>()
    private val redisPaymentRepository = mockk<RedisPaymentRepository>()
    private val accountApiClient = mockk<AccountApiClient>()
    private val orderApiClient = mockk<OrderApiClient>()
    private val outboxRepository = mockk<OutboxRepository>()
    private val objectMapper = mockk<ObjectMapper>()
    private val applicationContext = mockk<ApplicationContext>()

    private lateinit var paymentService: PaymentService

    @BeforeEach
    fun setUp() {
        paymentService = PaymentService(
            paymentRepository,
            redisPaymentRepository,
            accountApiClient,
            orderApiClient,
            outboxRepository,
            objectMapper
        )
        val field = PaymentService::class.java.getDeclaredField("applicationContext")
        field.isAccessible = true
        field.set(paymentService, applicationContext)
        every { applicationContext.getBean(PaymentService::class.java) } returns paymentService
    }

    // ========================
    // processPayment - 성공
    // ========================
    @Test
    fun `processPayment - 결제 성공 시 COMPLETED 반환`() {
        val (orderId, memberId, idempotencyKey, amount) = fixture()
        val reservationInfo = ReservationInfo(orderId, memberId, amount, "PENDING", false)
        val processingPayment = buildPayment(1L, orderId, memberId, amount, PaymentStatus.PROCESSING)
        val completedPayment = processingPayment.copy(status = PaymentStatus.COMPLETED)
        val outboxEvent = mockk<OutboxEvent>()

        every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
        every { redisPaymentRepository.registerIdempotencyKey(idempotencyKey, any()) } returns true
        every { orderApiClient.getReservationInfo(orderId) } returns reservationInfo
        every { paymentRepository.findByOrderId(orderId) } returns null
        every { paymentRepository.save(match { it.status == PaymentStatus.PROCESSING }) } returns processingPayment
        justRun { accountApiClient.withdraw(memberId, amount, any()) }
        justRun { orderApiClient.confirmReservation(orderId) }
        every { paymentRepository.save(match { it.status == PaymentStatus.COMPLETED }) } returns completedPayment
        every { objectMapper.writeValueAsString(any()) } returns "{}"
        every { outboxRepository.save(any()) } returns outboxEvent

        val result = paymentService.processPayment(orderId, memberId, idempotencyKey)

        assertThat(result.status).isEqualTo(PaymentStatus.COMPLETED)
        verify(exactly = 1) { accountApiClient.withdraw(memberId, amount, any()) }
        verify(exactly = 1) { orderApiClient.confirmReservation(orderId) }
        verify(exactly = 0) { accountApiClient.deposit(any(), any(), any()) }
        verify(exactly = 0) { orderApiClient.cancelReservation(any()) }
    }

    // ========================
    // processPayment - 멱등성
    // ========================
    @Test
    fun `processPayment - 멱등성 키 중복 시 기존 결제 반환`() {
        val idempotencyKey = "duplicate-key"
        val existingPayment = buildPayment(1L, 1L, 1L, BigDecimal("10000"), PaymentStatus.COMPLETED)

        every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns existingPayment

        val result = paymentService.processPayment(1L, 1L, idempotencyKey)

        assertThat(result).isEqualTo(existingPayment)
        verify(exactly = 0) { orderApiClient.getReservationInfo(any()) }
        verify(exactly = 0) { accountApiClient.withdraw(any(), any(), any()) }
    }

    @Test
    fun `processPayment - Redis 멱등성 키 충돌 시 DuplicatePaymentException`() {
        val (orderId, memberId, idempotencyKey, _) = fixture()

        every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
        every { redisPaymentRepository.registerIdempotencyKey(idempotencyKey, any()) } returns false
        every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null

        assertThatThrownBy {
            paymentService.processPayment(orderId, memberId, idempotencyKey)
        }.isInstanceOf(DuplicatePaymentException::class.java)
    }

    // ========================
    // processPayment - 선점 검증 실패
    // ========================
    @Test
    fun `processPayment - 선점 만료 시 결제 불가`() {
        val (orderId, memberId, idempotencyKey, amount) = fixture()
        val expiredReservation = ReservationInfo(orderId, memberId, amount, "PENDING", isExpired = true)

        every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
        every { redisPaymentRepository.registerIdempotencyKey(idempotencyKey, any()) } returns true
        every { orderApiClient.getReservationInfo(orderId) } returns expiredReservation
        every { paymentRepository.findByOrderId(orderId) } returns null

        assertThatThrownBy {
            paymentService.processPayment(orderId, memberId, idempotencyKey)
        }.isInstanceOf(OrderNotPayableForPaymentException::class.java)
    }

    @Test
    fun `processPayment - 타 회원 선점으로 결제 불가`() {
        val (orderId, memberId, idempotencyKey, amount) = fixture()
        val otherMemberReservation = ReservationInfo(orderId, memberId = 999L, amount, "PENDING", false)

        every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
        every { redisPaymentRepository.registerIdempotencyKey(idempotencyKey, any()) } returns true
        every { orderApiClient.getReservationInfo(orderId) } returns otherMemberReservation
        every { paymentRepository.findByOrderId(orderId) } returns null

        assertThatThrownBy {
            paymentService.processPayment(orderId, memberId, idempotencyKey)
        }.isInstanceOf(OrderNotPayableForPaymentException::class.java)
    }

    @Test
    fun `processPayment - 이미 결제 완료된 주문 재결제 시 PaymentAlreadyCompletedException`() {
        val (orderId, memberId, idempotencyKey, amount) = fixture()
        val reservationInfo = ReservationInfo(orderId, memberId, amount, "PENDING", false)
        val completedPayment = buildPayment(1L, orderId, memberId, amount, PaymentStatus.COMPLETED)

        every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
        every { redisPaymentRepository.registerIdempotencyKey(idempotencyKey, any()) } returns true
        every { orderApiClient.getReservationInfo(orderId) } returns reservationInfo
        every { paymentRepository.findByOrderId(orderId) } returns completedPayment

        assertThatThrownBy {
            paymentService.processPayment(orderId, memberId, idempotencyKey)
        }.isInstanceOf(PaymentAlreadyCompletedException::class.java)
    }

    // ========================
    // processPayment - 보상 트랜잭션
    // ========================
    @Test
    fun `processPayment - 잔액 차감 실패 시 선점 취소 호출 및 FAILED`() {
        val (orderId, memberId, idempotencyKey, amount) = fixture()
        val reservationInfo = ReservationInfo(orderId, memberId, amount, "PENDING", false)
        val processingPayment = buildPayment(1L, orderId, memberId, amount, PaymentStatus.PROCESSING)

        every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
        every { redisPaymentRepository.registerIdempotencyKey(idempotencyKey, any()) } returns true
        every { orderApiClient.getReservationInfo(orderId) } returns reservationInfo
        every { paymentRepository.findByOrderId(orderId) } returns null
        every { paymentRepository.save(match { it.status == PaymentStatus.PROCESSING }) } returns processingPayment
        every { accountApiClient.withdraw(memberId, amount, any()) } throws RuntimeException("잔액 부족")
        justRun { orderApiClient.cancelReservation(orderId) }
        every { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) } returns processingPayment.copy(status = PaymentStatus.FAILED)

        assertThatThrownBy {
            paymentService.processPayment(orderId, memberId, idempotencyKey)
        }.isInstanceOf(PaymentProcessFailedException::class.java)

        verify(exactly = 0) { accountApiClient.deposit(any(), any(), any()) }
        verify(exactly = 1) { orderApiClient.cancelReservation(orderId) }
        verify(exactly = 1) { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
    }

    @Test
    fun `processPayment - 확정 주문 생성 실패 시 잔액 복구 및 선점 취소 호출`() {
        val (orderId, memberId, idempotencyKey, amount) = fixture()
        val reservationInfo = ReservationInfo(orderId, memberId, amount, "PENDING", false)
        val processingPayment = buildPayment(1L, orderId, memberId, amount, PaymentStatus.PROCESSING)

        every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
        every { redisPaymentRepository.registerIdempotencyKey(idempotencyKey, any()) } returns true
        every { orderApiClient.getReservationInfo(orderId) } returns reservationInfo
        every { paymentRepository.findByOrderId(orderId) } returns null
        every { paymentRepository.save(match { it.status == PaymentStatus.PROCESSING }) } returns processingPayment
        justRun { accountApiClient.withdraw(memberId, amount, any()) }
        every { orderApiClient.confirmReservation(orderId) } throws RuntimeException("주문 서버 오류")
        justRun { accountApiClient.deposit(memberId, amount, any()) }
        justRun { orderApiClient.cancelReservation(orderId) }
        every { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) } returns processingPayment.copy(status = PaymentStatus.FAILED)

        assertThatThrownBy {
            paymentService.processPayment(orderId, memberId, idempotencyKey)
        }.isInstanceOf(PaymentProcessFailedException::class.java)

        verify(exactly = 1) { accountApiClient.deposit(memberId, amount, any()) }
        verify(exactly = 1) { orderApiClient.cancelReservation(orderId) }
        verify(exactly = 1) { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
    }

    // ========================
    // refundPayment
    // ========================
    @Test
    fun `refundPayment - 환불 성공 시 잔액 복구 및 주문 취소`() {
        val paymentId = 1L
        val completedPayment = buildPayment(paymentId, 1L, 1L, BigDecimal("10000"), PaymentStatus.COMPLETED)
        val refundedPayment = completedPayment.copy(status = PaymentStatus.REFUNDED)
        val outboxEvent = mockk<OutboxEvent>()

        every { paymentRepository.findById(paymentId) } returns completedPayment
        justRun { accountApiClient.deposit(any(), any(), any()) }
        justRun { orderApiClient.cancelOrder(any()) }
        every { paymentRepository.save(match { it.status == PaymentStatus.REFUNDED }) } returns refundedPayment
        every { objectMapper.writeValueAsString(any()) } returns "{}"
        every { outboxRepository.save(any()) } returns outboxEvent

        val result = paymentService.refundPayment(paymentId)

        assertThat(result.status).isEqualTo(PaymentStatus.REFUNDED)
        verify(exactly = 1) { accountApiClient.deposit(any(), any(), any()) }
        verify(exactly = 1) { orderApiClient.cancelOrder(1L) }
    }

    @Test
    fun `refundPayment - 주문 취소 실패 시 잔액 재차감 보상 호출`() {
        val paymentId = 1L
        val amount = BigDecimal("10000")
        val completedPayment = buildPayment(paymentId, 1L, 1L, amount, PaymentStatus.COMPLETED)

        every { paymentRepository.findById(paymentId) } returns completedPayment
        justRun { accountApiClient.deposit(any(), any(), any()) }
        every { orderApiClient.cancelOrder(any()) } throws RuntimeException("주문 서버 오류")
        justRun { accountApiClient.withdraw(any(), any(), any()) }

        assertThatThrownBy {
            paymentService.refundPayment(paymentId)
        }.isInstanceOf(RuntimeException::class.java)

        verify(exactly = 1) { accountApiClient.deposit(any(), any(), any()) }
        verify(exactly = 1) { accountApiClient.withdraw(completedPayment.memberId, amount, any()) }
    }

    @Test
    fun `refundPayment - COMPLETED 아닌 결제 환불 시 PaymentNotRefundableException`() {
        val paymentId = 1L
        val failedPayment = buildPayment(paymentId, 1L, 1L, BigDecimal("10000"), PaymentStatus.FAILED)

        every { paymentRepository.findById(paymentId) } returns failedPayment

        assertThatThrownBy {
            paymentService.refundPayment(paymentId)
        }.isInstanceOf(PaymentNotRefundableException::class.java)
    }

    // ========================
    // helpers
    // ========================
    private data class Fixture(val orderId: Long, val memberId: Long, val idempotencyKey: String, val amount: BigDecimal)

    private fun fixture() = Fixture(1L, 1L, "test-key", BigDecimal("10000"))

    private fun buildPayment(
        id: Long, orderId: Long, memberId: Long, amount: BigDecimal, status: PaymentStatus
    ) = Payment(
        id = id,
        orderId = orderId,
        memberId = memberId,
        amount = amount,
        status = status,
        idempotencyKey = "key-$id",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
}
