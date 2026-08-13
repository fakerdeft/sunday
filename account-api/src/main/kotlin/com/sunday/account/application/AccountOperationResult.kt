package com.sunday.account.application

import com.sunday.account.domain.TransactionType
import java.math.BigDecimal

data class AccountOperationResult(
    val operationId: String,
    val memberId: Long,
    val transactionType: TransactionType,
    val amount: BigDecimal
)
