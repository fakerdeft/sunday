package com.sunday.account.presentation.dto

import com.sunday.account.domain.Account
import com.sunday.account.domain.AccountTransaction
import java.math.BigDecimal

data class CreateAccountRequest(
    val memberId: Long,
    val userId: String
)

data class DepositRequest(
    val amount: BigDecimal,
    val description: String? = null
)

data class WithdrawRequest(
    val amount: BigDecimal,
    val description: String? = null
)

data class AccountResponse(
    val id: Long,
    val memberId: Long,
    val userId: String,
    val balance: BigDecimal,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(account: Account): AccountResponse = AccountResponse(
            id = account.id,
            memberId = account.memberId,
            userId = account.userId,
            balance = account.balance,
            createdAt = account.createdAt.toString(),
            updatedAt = account.updatedAt.toString()
        )
    }
}

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
