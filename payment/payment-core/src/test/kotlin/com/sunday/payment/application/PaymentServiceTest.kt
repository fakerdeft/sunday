package com.sunday.payment.application

import com.sunday.payment.domain.Payment
import com.sunday.payment.domain.PaymentStatus
import com.sunday.payment.exception.OrderNotPayableException
import com.sunday.payment.exception.PaymentNotFoundException
import com.sunday.payment.port.outbound.AccountPort
import com.sunday.payment.port.outbound.OrderInfo
import com.sunday.payment.port.outbound.OrderPort
import com.sunday.payment.port.outbound.OutboxPort
import com.sunday.payment.port.outbound.PaymentLockRepository
import com.sunday.payment.port.outbound.PaymentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class PaymentServiceTest : DescribeSpec({

    val paymentRepository = mockk<PaymentRepository>()
    val paymentLockRepository = mockk<PaymentLockRepository>()
    val accountPort = mockk<AccountPort>(relaxed = true)
    val orderPort = mockk<OrderPort>(relaxed = true)
    val outboxPort = mockk<OutboxPort>(relaxed = true)
    val paymentService = PaymentService(paymentRepository, paymentLockRepository, accountPort, orderPort, outboxPort)

    describe("processPayment") {
        context("이미 처리된 멱등성 키로 요청하면") {
            it("기존 Payment를 반환한다") {
                val existingPayment = Payment(
                    id = 1L,
                    orderId = 100L,
                    memberId = 1L,
                    amount = BigDecimal("50000"),
                    status = PaymentStatus.COMPLETED,
                    idempotencyKey = "pay-key-123"
                )
                every { paymentRepository.findByIdempotencyKey("pay-key-123") } returns existingPayment

                val result = paymentService.processPayment(100L, 1L, "pay-key-123")

                result.id shouldBe 1L
                result.status shouldBe PaymentStatus.COMPLETED
            }
        }

        context("주문이 결제 가능한 상태가 아니면") {
            it("OrderNotPayableException이 발생한다") {
                every { paymentRepository.findByIdempotencyKey("pay-key-123") } returns null
                every { paymentLockRepository.registerIdempotencyKey("pay-key-123", any()) } returns true
                every { orderPort.getOrderInfo(100L) } returns OrderInfo(
                    orderId = 100L,
                    memberId = 1L,
                    totalAmount = BigDecimal("50000"),
                    status = "PAID",
                    isExpired = false
                )

                shouldThrow<OrderNotPayableException> {
                    paymentService.processPayment(100L, 1L, "pay-key-123")
                }
            }
        }

        context("다른 회원의 주문이면") {
            it("OrderNotPayableException이 발생한다") {
                every { paymentRepository.findByIdempotencyKey("pay-key-123") } returns null
                every { paymentLockRepository.registerIdempotencyKey("pay-key-123", any()) } returns true
                every { orderPort.getOrderInfo(100L) } returns OrderInfo(
                    orderId = 100L,
                    memberId = 999L,
                    totalAmount = BigDecimal("50000"),
                    status = "PENDING",
                    isExpired = false
                )

                shouldThrow<OrderNotPayableException> {
                    paymentService.processPayment(100L, 1L, "pay-key-123")
                }
            }
        }
    }

    describe("getPayment") {
        context("존재하는 결제 ID로 조회하면") {
            it("Payment를 반환한다") {
                val payment = Payment(
                    id = 1L,
                    orderId = 100L,
                    memberId = 1L,
                    amount = BigDecimal("50000"),
                    status = PaymentStatus.COMPLETED,
                    idempotencyKey = "pay-key-123"
                )
                every { paymentRepository.findById(1L) } returns payment

                val result = paymentService.getPayment(1L)

                result.id shouldBe 1L
                result.amount shouldBe BigDecimal("50000")
            }
        }

        context("존재하지 않는 결제 ID로 조회하면") {
            it("PaymentNotFoundException이 발생한다") {
                every { paymentRepository.findById(999L) } returns null

                shouldThrow<PaymentNotFoundException> {
                    paymentService.getPayment(999L)
                }
            }
        }
    }

    describe("getMyPayments") {
        context("회원의 결제 목록을 조회하면") {
            it("해당 회원의 결제 목록을 반환한다") {
                val payments = listOf(
                    Payment(
                        id = 1L,
                        orderId = 100L,
                        memberId = 1L,
                        amount = BigDecimal("50000"),
                        status = PaymentStatus.COMPLETED,
                        idempotencyKey = "key-1"
                    ),
                    Payment(
                        id = 2L,
                        orderId = 101L,
                        memberId = 1L,
                        amount = BigDecimal("30000"),
                        status = PaymentStatus.COMPLETED,
                        idempotencyKey = "key-2"
                    )
                )
                every { paymentRepository.findByMemberId(1L) } returns payments

                val result = paymentService.getMyPayments(1L)

                result.size shouldBe 2
            }
        }
    }

    describe("refundPayment") {
        context("완료된 결제를 환불하면") {
            it("REFUNDED 상태가 되고 잔액이 복구된다") {
                val payment = Payment(
                    id = 1L,
                    orderId = 100L,
                    memberId = 1L,
                    amount = BigDecimal("50000"),
                    status = PaymentStatus.COMPLETED,
                    idempotencyKey = "pay-key-123"
                )
                every { paymentRepository.findById(1L) } returns payment
                every { paymentRepository.save(any()) } answers { firstArg() }

                val result = paymentService.refundPayment(1L)

                result.status shouldBe PaymentStatus.REFUNDED
                verify { accountPort.deposit(1L, BigDecimal("50000"), any()) }
                verify { orderPort.cancelOrder(100L) }
            }
        }
    }
})
