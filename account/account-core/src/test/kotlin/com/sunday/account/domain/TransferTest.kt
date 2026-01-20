package com.sunday.account.domain

import com.sunday.account.exception.TransferNotReversibleException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class TransferTest : FunSpec({

    test("Transfer 정상 생성") {
        val transfer = Transfer(
            id = 1L,
            senderAccountId = 100L,
            senderMemberId = 1L,
            receiverAccountId = 200L,
            receiverMemberId = 2L,
            amount = BigDecimal("5000"),
            status = TransferStatus.PENDING,
            idempotencyKey = "key-123",
            description = "송금 테스트"
        )

        transfer.senderAccountId shouldBe 100L
        transfer.receiverAccountId shouldBe 200L
        transfer.amount shouldBe BigDecimal("5000")
        transfer.status shouldBe TransferStatus.PENDING
    }

    test("Transfer.create로 새 송금 생성 시 PENDING 상태") {
        val transfer = Transfer.create(
            senderAccountId = 100L,
            senderMemberId = 1L,
            receiverAccountId = 200L,
            receiverMemberId = 2L,
            amount = BigDecimal("5000"),
            idempotencyKey = "key-123"
        )

        transfer.id shouldBe 0L
        transfer.status shouldBe TransferStatus.PENDING
    }

    test("금액이 0 이하면 예외 발생") {
        shouldThrow<IllegalArgumentException> {
            Transfer.create(
                senderAccountId = 100L,
                senderMemberId = 1L,
                receiverAccountId = 200L,
                receiverMemberId = 2L,
                amount = BigDecimal.ZERO,
                idempotencyKey = "key-123"
            )
        }
    }

    test("같은 계좌로 송금 시 예외 발생") {
        shouldThrow<IllegalArgumentException> {
            Transfer.create(
                senderAccountId = 100L,
                senderMemberId = 1L,
                receiverAccountId = 100L,
                receiverMemberId = 1L,
                amount = BigDecimal("5000"),
                idempotencyKey = "key-123"
            )
        }
    }

    test("idempotencyKey가 빈 문자열이면 예외 발생") {
        shouldThrow<IllegalArgumentException> {
            Transfer.create(
                senderAccountId = 100L,
                senderMemberId = 1L,
                receiverAccountId = 200L,
                receiverMemberId = 2L,
                amount = BigDecimal("5000"),
                idempotencyKey = ""
            )
        }
    }

    test("complete - PENDING 상태에서 COMPLETED로 변경") {
        val transfer = Transfer.create(
            senderAccountId = 100L,
            senderMemberId = 1L,
            receiverAccountId = 200L,
            receiverMemberId = 2L,
            amount = BigDecimal("5000"),
            idempotencyKey = "key-123"
        )

        val completed = transfer.complete()

        completed.status shouldBe TransferStatus.COMPLETED
    }

    test("complete - PENDING이 아닌 상태에서 호출 시 예외 발생") {
        val transfer = Transfer.create(
            senderAccountId = 100L,
            senderMemberId = 1L,
            receiverAccountId = 200L,
            receiverMemberId = 2L,
            amount = BigDecimal("5000"),
            idempotencyKey = "key-123"
        ).complete()

        shouldThrow<IllegalStateException> {
            transfer.complete()
        }
    }

    test("fail - PENDING 상태에서 FAILED로 변경") {
        val transfer = Transfer.create(
            senderAccountId = 100L,
            senderMemberId = 1L,
            receiverAccountId = 200L,
            receiverMemberId = 2L,
            amount = BigDecimal("5000"),
            idempotencyKey = "key-123"
        )

        val failed = transfer.fail("잔액 부족")

        failed.status shouldBe TransferStatus.FAILED
        failed.failureReason shouldBe "잔액 부족"
    }

    test("reverse - COMPLETED 상태에서 REVERSED로 변경") {
        val transfer = Transfer.create(
            senderAccountId = 100L,
            senderMemberId = 1L,
            receiverAccountId = 200L,
            receiverMemberId = 2L,
            amount = BigDecimal("5000"),
            idempotencyKey = "key-123"
        ).complete()

        val reversed = transfer.reverse()

        reversed.status shouldBe TransferStatus.REVERSED
    }

    test("reverse - COMPLETED가 아닌 상태에서 호출 시 예외 발생") {
        val pendingTransfer = Transfer.create(
            senderAccountId = 100L,
            senderMemberId = 1L,
            receiverAccountId = 200L,
            receiverMemberId = 2L,
            amount = BigDecimal("5000"),
            idempotencyKey = "key-123"
        )

        shouldThrow<TransferNotReversibleException> {
            pendingTransfer.reverse()
        }
    }
})
