package com.sunday.payment.client

import java.math.BigDecimal

internal data class ReservationResponse(
    val id: Long,
    val memberId: Long,
    val totalAmount: BigDecimal,
    val status: String,
    val expireAt: String
)
