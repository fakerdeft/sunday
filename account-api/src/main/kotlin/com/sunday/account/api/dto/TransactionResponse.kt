package com.sunday.account.api.dto

import com.sunday.account.domain.AccountTransaction
import java.math.BigDecimal

data class TransactionResponse(
    val id: Long,
    val transactionType: String,
    val amount: BigDecimal,
    val balanceAfter: BigDecimal,
    val description: String?,
    val createdAt: String
) {
    companion object {
        fun from(transaction: AccountTransaction): TransactionResponse = TransactionResponse(
            id = transaction.id,
            transactionType = transaction.transactionType.name,
            amount = transaction.amount,
            balanceAfter = transaction.balanceAfter,
            description = transaction.description,
            createdAt = transaction.createdAt.toString()
        )
    }
}
