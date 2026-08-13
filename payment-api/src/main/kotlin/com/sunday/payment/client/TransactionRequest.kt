package com.sunday.payment.client

import java.math.BigDecimal

data class TransactionRequest(
    val amount: BigDecimal,
    val description: String,
    val operationId: String
)
