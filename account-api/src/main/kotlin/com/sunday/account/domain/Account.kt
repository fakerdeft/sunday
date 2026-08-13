package com.sunday.account.domain

import java.math.BigDecimal
import java.time.LocalDateTime

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
        if (userId.isBlank()) throw InvalidAccountUserIdException()
        if (balance < BigDecimal.ZERO) throw InvalidAccountBalanceException(balance)
    }

    companion object {
        fun create(memberId: Long, userId: String): Account =
            Account(id = 0L, memberId = memberId, userId = userId, balance = BigDecimal.ZERO, version = 0L)
    }

    fun deposit(
        amount: BigDecimal,
        description: String? = null,
        operationId: String? = null
    ): Pair<Account, AccountTransaction> {
        if (amount <= BigDecimal.ZERO) throw InvalidTransactionAmountException(amount)
        val newBalance = balance + amount
        val updatedAccount = copy(balance = newBalance, updatedAt = LocalDateTime.now())
        val transaction = AccountTransaction.create(
            accountId = id,
            type = TransactionType.DEPOSIT,
            amount = amount,
            balanceAfter = newBalance,
            description = description ?: "예치금 충전",
            operationId = operationId
        )

        return updatedAccount to transaction
    }

    fun withdraw(
        amount: BigDecimal,
        description: String? = null,
        operationId: String? = null
    ): Pair<Account, AccountTransaction> {
        if (amount <= BigDecimal.ZERO) throw InvalidTransactionAmountException(amount)
        if (balance < amount) throw InsufficientBalanceException(balance, amount)
        val newBalance = balance - amount
        val updatedAccount = copy(balance = newBalance, updatedAt = LocalDateTime.now())
        val transaction = AccountTransaction.create(
            accountId = id,
            type = TransactionType.WITHDRAWAL,
            amount = amount,
            balanceAfter = newBalance,
            description = description ?: "예치금 차감",
            operationId = operationId
        )

        return updatedAccount to transaction
    }
}
