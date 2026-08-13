package com.sunday.account.api.dto

import com.sunday.account.application.AccountOperationResult
import java.math.BigDecimal

data class AccountOperationResponse(
    val found: Boolean,
    val memberId: Long? = null,
    val transactionType: String? = null,
    val amount: BigDecimal? = null
) {
    companion object {
        fun from(operation: AccountOperationResult?): AccountOperationResponse {
            if (operation == null) {

                return AccountOperationResponse(found = false)
            }

            return AccountOperationResponse(
                found = true,
                memberId = operation.memberId,
                transactionType = operation.transactionType.name,
                amount = operation.amount
            )
        }
    }
}
