package com.sunday.payment.domain

import com.sunday.payment.exception.InvalidIdempotencyKeyException
import com.sunday.payment.exception.InvalidPaymentAmountException
import com.sunday.payment.exception.PaymentNotCompletableException
import com.sunday.payment.exception.PaymentNotFailableException
import com.sunday.payment.exception.PaymentNotRefundableException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class PaymentTest : FunSpec({

    test("Payment 정상 생성") {
        val payment = Payment(
            id = 1L,
            orderId = 100L,
            memberId = 1L,
            amount = BigDecimal("50000"),
            status = PaymentStatus.PROCESSING,
            idempotencyKey = "pay-key-123"
        )

        payment.orderId shouldBe 100L
        payment.amount shouldBe BigDecimal("50000")
        payment.status shouldBe PaymentStatus.PROCESSING
    }

    test("Payment.create로 새 결제 생성 시 PROCESSING 상태") {
        val payment = Payment.create(
            orderId = 100L,
            memberId = 1L,
            amount = BigDecimal("50000"),
            idempotencyKey = "pay-key-123"
        )

        payment.id shouldBe 0L
        payment.status shouldBe PaymentStatus.PROCESSING
    }

    test("금액이 0 이하면 예외 발생") {
        shouldThrow<InvalidPaymentAmountException> {
            Payment.create(
                orderId = 100L,
                memberId = 1L,
                amount = BigDecimal.ZERO,
                idempotencyKey = "pay-key-123"
            )
        }

        shouldThrow<InvalidPaymentAmountException> {
            Payment.create(
                orderId = 100L,
                memberId = 1L,
                amount = BigDecimal("-1000"),
                idempotencyKey = "pay-key-123"
            )
        }
    }

    test("idempotencyKey가 빈 문자열이면 예외 발생") {
        shouldThrow<InvalidIdempotencyKeyException> {
            Payment.create(
                orderId = 100L,
                memberId = 1L,
                amount = BigDecimal("50000"),
                idempotencyKey = ""
            )
        }
    }

    test("complete - PROCESSING 상태에서 COMPLETED로 변경") {
        val payment = Payment.create(
            orderId = 100L,
            memberId = 1L,
            amount = BigDecimal("50000"),
            idempotencyKey = "pay-key-123"
        )

        val completed = payment.complete()

        completed.status shouldBe PaymentStatus.COMPLETED
    }

    test("complete - PROCESSING이 아닌 상태에서 호출 시 예외 발생") {
        val payment = Payment.create(
            orderId = 100L,
            memberId = 1L,
            amount = BigDecimal("50000"),
            idempotencyKey = "pay-key-123"
        ).complete()

        shouldThrow<PaymentNotCompletableException> {
            payment.complete()
        }
    }

    test("fail - PROCESSING 상태에서 FAILED로 변경") {
        val payment = Payment.create(
            orderId = 100L,
            memberId = 1L,
            amount = BigDecimal("50000"),
            idempotencyKey = "pay-key-123"
        )

        val failed = payment.fail("잔액 부족")

        failed.status shouldBe PaymentStatus.FAILED
        failed.failureReason shouldBe "잔액 부족"
    }

    test("fail - PROCESSING이 아닌 상태에서 호출 시 예외 발생") {
        val payment = Payment.create(
            orderId = 100L,
            memberId = 1L,
            amount = BigDecimal("50000"),
            idempotencyKey = "pay-key-123"
        ).complete()

        shouldThrow<PaymentNotFailableException> {
            payment.fail("실패 사유")
        }
    }

    test("refund - COMPLETED 상태에서 REFUNDED로 변경") {
        val payment = Payment.create(
            orderId = 100L,
            memberId = 1L,
            amount = BigDecimal("50000"),
            idempotencyKey = "pay-key-123"
        ).complete()

        val refunded = payment.refund()

        refunded.status shouldBe PaymentStatus.REFUNDED
    }

    test("refund - COMPLETED가 아닌 상태에서 호출 시 예외 발생") {
        val processingPayment = Payment.create(
            orderId = 100L,
            memberId = 1L,
            amount = BigDecimal("50000"),
            idempotencyKey = "pay-key-123"
        )

        shouldThrow<PaymentNotRefundableException> {
            processingPayment.refund()
        }

        val failedPayment = processingPayment.fail("실패")

        shouldThrow<PaymentNotRefundableException> {
            failedPayment.refund()
        }
    }
})
