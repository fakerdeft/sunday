package com.sunday.account.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class AccountTransaction(
    val id: Long,
    val accountId: Long,
    val transactionType: TransactionType,
    val amount: BigDecimal,
    val balanceAfter: BigDecimal,
    val description: String?,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        if (amount <= BigDecimal.ZERO) throw InvalidTransactionAmountException(amount)
        if (balanceAfter < BigDecimal.ZERO) throw InvalidAccountBalanceException(balanceAfter)
    }

    companion object {
        fun create(
            accountId: Long,
            type: TransactionType,
            amount: BigDecimal,
            balanceAfter: BigDecimal,
            description: String?
        ): AccountTransaction {
            return AccountTransaction(
                id = 0L,
                accountId = accountId,
                transactionType = type,
                amount = amount,
                balanceAfter = balanceAfter,
                description = description
            )
        }
    }
}
