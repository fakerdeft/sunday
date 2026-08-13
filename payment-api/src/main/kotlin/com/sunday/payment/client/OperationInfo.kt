package com.sunday.payment.client

import java.math.BigDecimal

data class OperationInfo(
    val found: Boolean,
    val memberId: Long? = null,
    val transactionType: String? = null,
    val amount: BigDecimal? = null
)
