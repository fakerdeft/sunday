package com.sunday.account.domain

import com.sunday.account.exception.InvalidAccountBalanceException
import com.sunday.account.exception.InvalidTransactionAmountException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class AccountTransactionTest : FunSpec({

    test("AccountTransaction 정상 생성") {
        val transaction = AccountTransaction(
            id = 1L,
            accountId = 100L,
            transactionType = TransactionType.DEPOSIT,
            amount = BigDecimal("5000"),
            balanceAfter = BigDecimal("15000"),
            description = "테스트 충전"
        )

        transaction.accountId shouldBe 100L
        transaction.transactionType shouldBe TransactionType.DEPOSIT
        transaction.amount shouldBe BigDecimal("5000")
        transaction.balanceAfter shouldBe BigDecimal("15000")
    }

    test("AccountTransaction.create로 새 거래 생성") {
        val transaction = AccountTransaction.create(
            accountId = 100L,
            type = TransactionType.WITHDRAWAL,
            amount = BigDecimal("3000"),
            balanceAfter = BigDecimal("7000"),
            description = "테스트 출금"
        )

        transaction.id shouldBe 0L
        transaction.accountId shouldBe 100L
        transaction.transactionType shouldBe TransactionType.WITHDRAWAL
    }

    test("금액이 0 이하면 예외 발생") {
        shouldThrow<InvalidTransactionAmountException> {
            AccountTransaction.create(
                accountId = 100L,
                type = TransactionType.DEPOSIT,
                amount = BigDecimal.ZERO,
                balanceAfter = BigDecimal("10000"),
                description = null
            )
        }

        shouldThrow<InvalidTransactionAmountException> {
            AccountTransaction.create(
                accountId = 100L,
                type = TransactionType.DEPOSIT,
                amount = BigDecimal("-1000"),
                balanceAfter = BigDecimal("10000"),
                description = null
            )
        }
    }

    test("거래 후 잔액이 음수면 예외 발생") {
        shouldThrow<InvalidAccountBalanceException> {
            AccountTransaction.create(
                accountId = 100L,
                type = TransactionType.WITHDRAWAL,
                amount = BigDecimal("5000"),
                balanceAfter = BigDecimal("-1000"),
                description = null
            )
        }
    }
})
