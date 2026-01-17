package com.sunday.account.domain

import com.sunday.account.exception.InsufficientBalanceException
import com.sunday.account.exception.InvalidAccountBalanceException
import com.sunday.account.exception.InvalidAccountUserIdException
import com.sunday.account.exception.InvalidTransactionAmountException
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Account 도메인 모델 (예치금 계좌)
 *
 * - 순수 비즈니스 로직 (Spring 의존성 없음)
 * - 불변성 보장 (data class + copy)
 * - 낙관적 락을 위한 version 필드 포함
 * - 도메인이 스스로 불변성 검증 (Service에서 중복 검증 불필요)
 */
data class Account(
    val id: Long,
    val memberId: Long,
    val userId: String,
    val balance: BigDecimal,
    val version: Long,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        if (userId.isBlank()) {
            throw InvalidAccountUserIdException()
        }

        if (balance < BigDecimal.ZERO) {
            throw InvalidAccountBalanceException(balance)
        }
    }

    companion object {
        fun create(memberId: Long, userId: String): Account {
            return Account(
                id = 0L,
                memberId = memberId,
                userId = userId,
                balance = BigDecimal.ZERO,
                version = 0L
            )
        }
    }

    /**
     * 잔액 충전 (deposit)
     */
    fun deposit(amount: BigDecimal, description: String? = null): Pair<Account, AccountTransaction> {
        if (amount <= BigDecimal.ZERO) {
            throw InvalidTransactionAmountException(amount)
        }

        val newBalance = balance + amount
        val updatedAccount = this.copy(
            balance = newBalance,
            updatedAt = LocalDateTime.now()
        )

        val transaction = AccountTransaction.create(
            accountId = this.id,
            type = TransactionType.DEPOSIT,
            amount = amount,
            balanceAfter = newBalance,
            description = description ?: "예치금 충전"
        )

        return updatedAccount to transaction
    }

    /**
     * 잔액 차감 (withdraw)
     *
     * @throws InsufficientBalanceException 잔액 부족 시
     */
    fun withdraw(amount: BigDecimal, description: String? = null): Pair<Account, AccountTransaction> {
        if (amount <= BigDecimal.ZERO) {
            throw InvalidTransactionAmountException(amount)
        }

        if (balance < amount) {
            throw InsufficientBalanceException(balance, amount)
        }

        val newBalance = balance - amount
        val updatedAccount = this.copy(
            balance = newBalance,
            updatedAt = LocalDateTime.now()
        )

        val transaction = AccountTransaction.create(
            accountId = this.id,
            type = TransactionType.WITHDRAWAL,
            amount = amount,
            balanceAfter = newBalance,
            description = description ?: "예치금 차감"
        )

        return updatedAccount to transaction
    }
}
