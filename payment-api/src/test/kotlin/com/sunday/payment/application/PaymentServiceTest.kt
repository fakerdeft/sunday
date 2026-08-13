package com.sunday.payment.application

import com.sunday.payment.client.AccountApiClient
import com.sunday.payment.client.OperationInfo
import com.sunday.payment.client.OrderApiClient
import com.sunday.payment.client.ReservationInfo
import com.sunday.payment.domain.Payment
import com.sunday.payment.domain.PaymentStatus
import com.sunday.payment.domain.exception.DuplicatePaymentException
import com.sunday.payment.domain.exception.PaymentNotFoundException
import com.sunday.payment.domain.exception.PaymentNotRefundableException
import com.sunday.payment.domain.exception.PaymentProcessFailedException
import com.sunday.payment.repository.PaymentRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class PaymentServiceTest {

    private lateinit var paymentRepository: PaymentRepository
    private lateinit var accountApiClient: AccountApiClient
    private lateinit var orderApiClient: OrderApiClient
    private lateinit var paymentTransactionService: PaymentTransactionService
    private lateinit var paymentService: PaymentService

    @BeforeEach
    fun setUp() {
        paymentRepository = mockk()
        accountApiClient = mockk()
        orderApiClient = mockk()
        paymentTransactionService = PaymentTransactionService(paymentRepository)
        paymentService = PaymentService(
            paymentTransactionService,
            accountApiClient,
            orderApiClient
        )
    }

    @Test
    fun `payment advances through acknowledged stages`() {
        val processing = payment(status = PaymentStatus.PROCESSING)
        val debited = processing.copy(status = PaymentStatus.ACCOUNT_DEBITED)
        val confirmed = processing.copy(status = PaymentStatus.ORDER_CONFIRMED)
        val completed = processing.copy(status = PaymentStatus.COMPLETED)
        val reservation = reservation(status = "PENDING")

        every { paymentRepository.findByIdempotencyKey(KEY) } returns null
        every { paymentRepository.findByOrderId(ORDER_ID) } returns null
        every { orderApiClient.getReservationInfo(ORDER_ID) } returns reservation
        every { paymentRepository.saveAndFlush(any()) } returns processing
        justRun { accountApiClient.withdraw(MEMBER_ID, AMOUNT, any(), "payment:1:charge") }
        every { paymentRepository.findByIdForUpdate(1L) } returnsMany listOf(processing, debited, confirmed)
        every { paymentRepository.save(match { it.status == PaymentStatus.ACCOUNT_DEBITED }) } returns debited
        justRun { orderApiClient.confirmReservation(ORDER_ID) }
        every { paymentRepository.save(match { it.status == PaymentStatus.ORDER_CONFIRMED }) } returns confirmed
        every { paymentRepository.save(match { it.status == PaymentStatus.COMPLETED }) } returns completed

        val result = paymentService.processPayment(ORDER_ID, MEMBER_ID, KEY)

        assertThat(result.status).isEqualTo(PaymentStatus.COMPLETED)
        verify(exactly = 1) { accountApiClient.withdraw(MEMBER_ID, AMOUNT, any(), "payment:1:charge") }
        verify(exactly = 1) { orderApiClient.confirmReservation(ORDER_ID) }
        verify(exactly = 0) { accountApiClient.deposit(any(), any(), any(), any()) }
    }

    @Test
    fun `completed payment retry returns existing result without external calls`() {
        val completed = payment(status = PaymentStatus.COMPLETED)

        every { paymentRepository.findByIdempotencyKey(KEY) } returns completed

        val result = paymentService.processPayment(ORDER_ID, MEMBER_ID, KEY)

        assertThat(result).isEqualTo(completed)
        verify(exactly = 0) { orderApiClient.getReservationInfo(any()) }
        verify(exactly = 0) { accountApiClient.withdraw(any(), any(), any(), any()) }
    }

    @Test
    fun `different key for an already paid order is rejected before calling order API`() {
        val completed = payment(status = PaymentStatus.COMPLETED)

        every { paymentRepository.findByIdempotencyKey(OTHER_KEY) } returns null
        every { paymentRepository.findByOrderId(ORDER_ID) } returns completed

        assertThatThrownBy { paymentService.processPayment(ORDER_ID, MEMBER_ID, OTHER_KEY) }
            .isInstanceOf(DuplicatePaymentException::class.java)

        verify(exactly = 0) { orderApiClient.getReservationInfo(any()) }
        verify(exactly = 0) { accountApiClient.withdraw(any(), any(), any(), any()) }
    }

    @Test
    fun `late concurrent request is classified as duplicate after order becomes confirmed`() {
        val completed = payment(status = PaymentStatus.COMPLETED)

        every { paymentRepository.findByIdempotencyKey(OTHER_KEY) } returns null
        every { paymentRepository.findByOrderId(ORDER_ID) } returnsMany listOf(null, completed)
        every { orderApiClient.getReservationInfo(ORDER_ID) } returns reservation(status = "CONFIRMED")

        assertThatThrownBy { paymentService.processPayment(ORDER_ID, MEMBER_ID, OTHER_KEY) }
            .isInstanceOf(DuplicatePaymentException::class.java)

        verify(exactly = 1) { orderApiClient.getReservationInfo(ORDER_ID) }
        verify(exactly = 0) { accountApiClient.withdraw(any(), any(), any(), any()) }
    }

    @Test
    fun `unknown debit response remains processing and retries with the same operation id`() {
        val processing = payment(status = PaymentStatus.PROCESSING)
        val debited = processing.copy(status = PaymentStatus.ACCOUNT_DEBITED)
        val confirmed = processing.copy(status = PaymentStatus.ORDER_CONFIRMED)
        val completed = processing.copy(status = PaymentStatus.COMPLETED)

        every { paymentRepository.findByIdempotencyKey(KEY) } returns processing
        every { orderApiClient.getReservationInfo(ORDER_ID) } returns reservation(status = "PENDING")
        every {
            accountApiClient.withdraw(MEMBER_ID, AMOUNT, any(), "payment:1:charge")
        } throws RuntimeException("timeout")
        every { accountApiClient.getOperation("payment:1:charge") } returns
            OperationInfo(found = false)

        assertThatThrownBy { paymentService.processPayment(ORDER_ID, MEMBER_ID, KEY) }
            .isInstanceOf(PaymentProcessFailedException::class.java)

        justRun { accountApiClient.withdraw(MEMBER_ID, AMOUNT, any(), "payment:1:charge") }
        every { paymentRepository.findByIdForUpdate(1L) } returnsMany listOf(processing, debited, confirmed)
        every { paymentRepository.save(match { it.status == PaymentStatus.ACCOUNT_DEBITED }) } returns debited
        justRun { orderApiClient.confirmReservation(ORDER_ID) }
        every { paymentRepository.save(match { it.status == PaymentStatus.ORDER_CONFIRMED }) } returns confirmed
        every { paymentRepository.save(match { it.status == PaymentStatus.COMPLETED }) } returns completed

        val retried = paymentService.processPayment(ORDER_ID, MEMBER_ID, KEY)

        assertThat(retried.status).isEqualTo(PaymentStatus.COMPLETED)
        verify(exactly = 2) { accountApiClient.withdraw(MEMBER_ID, AMOUNT, any(), "payment:1:charge") }
    }

    @Test
    fun `debit timeout continues when operation lookup proves the charge`() {
        val processing = payment(status = PaymentStatus.PROCESSING)
        val debited = processing.copy(status = PaymentStatus.ACCOUNT_DEBITED)
        val confirmed = processing.copy(status = PaymentStatus.ORDER_CONFIRMED)
        val completed = processing.copy(status = PaymentStatus.COMPLETED)

        every { paymentRepository.findByIdempotencyKey(KEY) } returns processing
        every { orderApiClient.getReservationInfo(ORDER_ID) } returns reservation(status = "PENDING")
        every {
            accountApiClient.withdraw(MEMBER_ID, AMOUNT, any(), "payment:1:charge")
        } throws RuntimeException("timeout after commit")
        every { accountApiClient.getOperation("payment:1:charge") } returns OperationInfo(
            found = true,
            memberId = MEMBER_ID,
            transactionType = "WITHDRAWAL",
            amount = AMOUNT
        )
        every { paymentRepository.findByIdForUpdate(1L) } returnsMany listOf(processing, debited, confirmed)
        every { paymentRepository.save(match { it.status == PaymentStatus.ACCOUNT_DEBITED }) } returns debited
        justRun { orderApiClient.confirmReservation(ORDER_ID) }
        every { paymentRepository.save(match { it.status == PaymentStatus.ORDER_CONFIRMED }) } returns confirmed
        every { paymentRepository.save(match { it.status == PaymentStatus.COMPLETED }) } returns completed

        val result = paymentService.processPayment(ORDER_ID, MEMBER_ID, KEY)

        assertThat(result.status).isEqualTo(PaymentStatus.COMPLETED)
        verify(exactly = 1) { accountApiClient.getOperation("payment:1:charge") }
        verify(exactly = 0) { accountApiClient.deposit(any(), any(), any(), any()) }
    }

    @Test
    fun `late debit discovered after expiry is reversed instead of charged again`() {
        val processing = payment(status = PaymentStatus.PROCESSING)
        val debited = processing.copy(status = PaymentStatus.ACCOUNT_DEBITED)
        val failed = processing.copy(status = PaymentStatus.FAILED, failureReason = "expired")

        every { paymentRepository.findByIdempotencyKey(KEY) } returns processing
        every { orderApiClient.getReservationInfo(ORDER_ID) } returns
            reservation(status = "EXPIRED", expired = true)
        every { accountApiClient.getOperation("payment:1:charge") } returns OperationInfo(
            found = true,
            memberId = MEMBER_ID,
            transactionType = "WITHDRAWAL",
            amount = AMOUNT
        )
        every { paymentRepository.findByIdForUpdate(1L) } returnsMany listOf(processing, debited)
        every { paymentRepository.save(match { it.status == PaymentStatus.ACCOUNT_DEBITED }) } returns debited
        justRun {
            accountApiClient.deposit(MEMBER_ID, AMOUNT, any(), "payment:1:charge-reversal")
        }
        every { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) } returns failed

        assertThatThrownBy { paymentService.processPayment(ORDER_ID, MEMBER_ID, KEY) }
            .isInstanceOf(PaymentProcessFailedException::class.java)

        verify(exactly = 0) { accountApiClient.withdraw(any(), any(), any(), any()) }
        verify(exactly = 1) {
            accountApiClient.deposit(MEMBER_ID, AMOUNT, any(), "payment:1:charge-reversal")
        }
    }

    @Test
    fun `confirmation timeout reconciles confirmed reservation without refund`() {
        val debited = payment(status = PaymentStatus.ACCOUNT_DEBITED)
        val confirmed = debited.copy(status = PaymentStatus.ORDER_CONFIRMED)
        val completed = debited.copy(status = PaymentStatus.COMPLETED)

        every { paymentRepository.findByIdempotencyKey(KEY) } returns debited
        every { orderApiClient.getReservationInfo(ORDER_ID) } returnsMany listOf(
            reservation(status = "PENDING"),
            reservation(status = "CONFIRMED")
        )
        every { orderApiClient.confirmReservation(ORDER_ID) } throws RuntimeException("timeout")
        every { paymentRepository.findByIdForUpdate(1L) } returnsMany listOf(debited, confirmed)
        every { paymentRepository.save(match { it.status == PaymentStatus.ORDER_CONFIRMED }) } returns confirmed
        every { paymentRepository.save(match { it.status == PaymentStatus.COMPLETED }) } returns completed

        val result = paymentService.processPayment(ORDER_ID, MEMBER_ID, KEY)

        assertThat(result.status).isEqualTo(PaymentStatus.COMPLETED)
        verify(exactly = 0) { accountApiClient.deposit(any(), any(), any(), any()) }
        verify(exactly = 0) { orderApiClient.cancelReservation(any()) }
    }

    @Test
    fun `expired reservation is cancelled before an idempotent charge reversal`() {
        val debited = payment(status = PaymentStatus.ACCOUNT_DEBITED)
        val failed = debited.copy(status = PaymentStatus.FAILED, failureReason = "expired")

        every { paymentRepository.findByIdempotencyKey(KEY) } returns debited
        every { orderApiClient.getReservationInfo(ORDER_ID) } returnsMany listOf(
            reservation(status = "PENDING", expired = true),
            reservation(status = "PENDING", expired = true),
            reservation(status = "CANCELLED", expired = true)
        )
        justRun { orderApiClient.cancelReservation(ORDER_ID) }
        justRun {
            accountApiClient.deposit(MEMBER_ID, AMOUNT, any(), "payment:1:charge-reversal")
        }
        every { paymentRepository.findByIdForUpdate(1L) } returns debited
        every { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) } returns failed

        assertThatThrownBy { paymentService.processPayment(ORDER_ID, MEMBER_ID, KEY) }
            .isInstanceOf(PaymentProcessFailedException::class.java)

        verify(exactly = 1) { orderApiClient.cancelReservation(ORDER_ID) }
        verify(exactly = 1) {
            accountApiClient.deposit(MEMBER_ID, AMOUNT, any(), "payment:1:charge-reversal")
        }
    }

    @Test
    fun `refund is resumable and credits the account only once`() {
        val completed = payment(status = PaymentStatus.COMPLETED)
        val refunding = completed.copy(status = PaymentStatus.REFUND_PROCESSING)
        val refunded = completed.copy(status = PaymentStatus.REFUNDED)

        every { paymentRepository.findByIdForUpdate(1L) } returnsMany listOf(completed, refunding, refunded)
        every { paymentRepository.save(match { it.status == PaymentStatus.REFUND_PROCESSING }) } returns refunding
        justRun { orderApiClient.cancelOrder(ORDER_ID) }
        justRun { accountApiClient.deposit(MEMBER_ID, AMOUNT, any(), "payment:1:refund") }
        every { paymentRepository.save(match { it.status == PaymentStatus.REFUNDED }) } returns refunded

        val first = paymentService.refundPayment(1L, MEMBER_ID)
        val retry = paymentService.refundPayment(1L, MEMBER_ID)

        assertThat(first.status).isEqualTo(PaymentStatus.REFUNDED)
        assertThat(retry.status).isEqualTo(PaymentStatus.REFUNDED)
        verify(exactly = 1) { orderApiClient.cancelOrder(ORDER_ID) }
        verify(exactly = 1) { accountApiClient.deposit(MEMBER_ID, AMOUNT, any(), "payment:1:refund") }
    }

    @Test
    fun `refund failure keeps refund processing state for retry`() {
        val completed = payment(status = PaymentStatus.COMPLETED)
        val refunding = completed.copy(status = PaymentStatus.REFUND_PROCESSING)
        val refunded = completed.copy(status = PaymentStatus.REFUNDED)

        every { paymentRepository.findByIdForUpdate(1L) } returnsMany listOf(completed, refunding, refunding)
        every { paymentRepository.save(match { it.status == PaymentStatus.REFUND_PROCESSING }) } returns refunding
        every { orderApiClient.cancelOrder(ORDER_ID) } throws RuntimeException("timeout")

        assertThatThrownBy { paymentService.refundPayment(1L, MEMBER_ID) }
            .isInstanceOf(PaymentProcessFailedException::class.java)

        justRun { orderApiClient.cancelOrder(ORDER_ID) }
        justRun { accountApiClient.deposit(MEMBER_ID, AMOUNT, any(), "payment:1:refund") }
        every { paymentRepository.save(match { it.status == PaymentStatus.REFUNDED }) } returns refunded

        val retried = paymentService.refundPayment(1L, MEMBER_ID)

        assertThat(retried.status).isEqualTo(PaymentStatus.REFUNDED)
        verify(exactly = 2) { orderApiClient.cancelOrder(ORDER_ID) }
        verify(exactly = 1) { accountApiClient.deposit(MEMBER_ID, AMOUNT, any(), "payment:1:refund") }
    }

    @Test
    fun `refund hides another members payment`() {
        every { paymentRepository.findByIdForUpdate(1L) } returns payment(status = PaymentStatus.COMPLETED)

        assertThatThrownBy { paymentService.refundPayment(1L, 999L) }
            .isInstanceOf(PaymentNotFoundException::class.java)

        verify(exactly = 0) { orderApiClient.cancelOrder(any()) }
        verify(exactly = 0) { accountApiClient.deposit(any(), any(), any(), any()) }
    }

    @Test
    fun `non completed payment cannot start refund`() {
        every { paymentRepository.findByIdForUpdate(1L) } returns payment(status = PaymentStatus.FAILED)

        assertThatThrownBy { paymentService.refundPayment(1L, MEMBER_ID) }
            .isInstanceOf(PaymentNotRefundableException::class.java)
    }

    private fun reservation(status: String, expired: Boolean = false) =
        ReservationInfo(ORDER_ID, MEMBER_ID, AMOUNT, status, expired)

    private fun payment(status: PaymentStatus) = Payment(
        id = 1L,
        orderId = ORDER_ID,
        memberId = MEMBER_ID,
        amount = AMOUNT,
        status = status,
        idempotencyKey = KEY,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    companion object {
        private const val ORDER_ID = 100L
        private const val MEMBER_ID = 10L
        private const val KEY = "payment-request-key"
        private const val OTHER_KEY = "another-payment-request-key"
        private val AMOUNT = BigDecimal("10000")
    }
}
