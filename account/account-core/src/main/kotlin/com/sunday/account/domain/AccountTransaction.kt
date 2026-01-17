package com.sunday.account.domain

import com.sunday.account.exception.InvalidAccountBalanceException
import com.sunday.account.exception.InvalidTransactionAmountException
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * AccountTransaction 도메인 모델 (거래 이력)
 *
 * - 불변성 보장 (생성 후 수정 불가)
 * - 감사(Audit) 목적의 거래 기록
 */
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
        if (amount <= BigDecimal.ZERO) {
            throw InvalidTransactionAmountException(amount)
        }

        if (balanceAfter < BigDecimal.ZERO) {
            throw InvalidAccountBalanceException(balanceAfter)
        }
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
