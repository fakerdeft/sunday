package com.sunday.account.domain

import com.sunday.account.exception.InsufficientBalanceException
import com.sunday.account.exception.InvalidAccountBalanceException
import com.sunday.account.exception.InvalidAccountUserIdException
import com.sunday.account.exception.InvalidTransactionAmountException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class AccountTest : FunSpec({

    test("Account 정상 생성") {
        val account = Account(
            id = 1L,
            memberId = 100L,
            userId = "user123",
            balance = BigDecimal("10000"),
            version = 0L
        )

        account.id shouldBe 1L
        account.memberId shouldBe 100L
        account.userId shouldBe "user123"
        account.balance shouldBe BigDecimal("10000")
    }

    test("Account.create로 새 계좌 생성 시 잔액 0원") {
        val account = Account.create(memberId = 100L, userId = "user123")

        account.id shouldBe 0L
        account.balance shouldBe BigDecimal.ZERO
        account.version shouldBe 0L
    }

    test("userId가 빈 문자열이면 예외 발생") {
        shouldThrow<InvalidAccountUserIdException> {
            Account.create(memberId = 100L, userId = "")
        }
    }

    test("userId가 공백만 있으면 예외 발생") {
        shouldThrow<InvalidAccountUserIdException> {
            Account.create(memberId = 100L, userId = "   ")
        }
    }

    test("잔액이 음수면 예외 발생") {
        shouldThrow<InvalidAccountBalanceException> {
            Account(
                id = 1L,
                memberId = 100L,
                userId = "user123",
                balance = BigDecimal("-1000"),
                version = 0L
            )
        }
    }

    test("deposit - 정상 충전") {
        val account = Account(
            id = 1L,
            memberId = 100L,
            userId = "user123",
            balance = BigDecimal("10000"),
            version = 0L
        )

        val (updatedAccount, transaction) = account.deposit(BigDecimal("5000"))

        updatedAccount.balance shouldBe BigDecimal("15000")
        transaction.transactionType shouldBe TransactionType.DEPOSIT
        transaction.amount shouldBe BigDecimal("5000")
        transaction.balanceAfter shouldBe BigDecimal("15000")
    }

    test("deposit - 0원 이하 충전 시 예외 발생") {
        val account = Account.create(memberId = 100L, userId = "user123")

        shouldThrow<InvalidTransactionAmountException> {
            account.deposit(BigDecimal.ZERO)
        }

        shouldThrow<InvalidTransactionAmountException> {
            account.deposit(BigDecimal("-1000"))
        }
    }

    test("withdraw - 정상 출금") {
        val account = Account(
            id = 1L,
            memberId = 100L,
            userId = "user123",
            balance = BigDecimal("10000"),
            version = 0L
        )

        val (updatedAccount, transaction) = account.withdraw(BigDecimal("3000"))

        updatedAccount.balance shouldBe BigDecimal("7000")
        transaction.transactionType shouldBe TransactionType.WITHDRAWAL
        transaction.amount shouldBe BigDecimal("3000")
        transaction.balanceAfter shouldBe BigDecimal("7000")
    }

    test("withdraw - 잔액 부족 시 예외 발생") {
        val account = Account(
            id = 1L,
            memberId = 100L,
            userId = "user123",
            balance = BigDecimal("1000"),
            version = 0L
        )

        shouldThrow<InsufficientBalanceException> {
            account.withdraw(BigDecimal("5000"))
        }
    }

    test("withdraw - 0원 이하 출금 시 예외 발생") {
        val account = Account(
            id = 1L,
            memberId = 100L,
            userId = "user123",
            balance = BigDecimal("10000"),
            version = 0L
        )

        shouldThrow<InvalidTransactionAmountException> {
            account.withdraw(BigDecimal.ZERO)
        }
    }
})
