package com.sunday.account.api.dto

import com.sunday.account.domain.Account
import java.math.BigDecimal

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
