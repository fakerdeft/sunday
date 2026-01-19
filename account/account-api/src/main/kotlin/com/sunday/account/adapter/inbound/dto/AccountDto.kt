package com.sunday.account.adapter.inbound.dto

import com.sunday.account.domain.Account
import com.sunday.account.domain.AccountTransaction
import com.sunday.account.domain.Transfer
import java.math.BigDecimal

// ===== Request DTOs =====

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

data class TransferRequest(
    val receiverMemberId: Long,
    val amount: BigDecimal,
    val idempotencyKey: String,
    val description: String? = null
)

// ===== Response DTOs =====

data class AccountResponse(
    val id: Long,
    val memberId: Long,
    val userId: String,
    val balance: BigDecimal,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(account: Account): AccountResponse {
            return AccountResponse(
                id = account.id,
                memberId = account.memberId,
                userId = account.userId,
                balance = account.balance,
                createdAt = account.createdAt.toString(),
                updatedAt = account.updatedAt.toString()
            )
        }
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
        fun from(transaction: AccountTransaction): TransactionResponse {
            return TransactionResponse(
                id = transaction.id,
                transactionType = transaction.transactionType.name,
                amount = transaction.amount,
                balanceAfter = transaction.balanceAfter,
                description = transaction.description,
                createdAt = transaction.createdAt.toString()
            )
        }
    }
}

data class TransferResponse(
    val id: Long,
    val senderMemberId: Long,
    val receiverMemberId: Long,
    val amount: BigDecimal,
    val status: String,
    val description: String?,
    val failureReason: String?,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(transfer: Transfer): TransferResponse {
            return TransferResponse(
                id = transfer.id,
                senderMemberId = transfer.senderMemberId,
                receiverMemberId = transfer.receiverMemberId,
                amount = transfer.amount,
                status = transfer.status.name,
                description = transfer.description,
                failureReason = transfer.failureReason,
                createdAt = transfer.createdAt.toString(),
                updatedAt = transfer.updatedAt.toString()
            )
        }
    }
}
