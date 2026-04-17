package com.sunday.payment.client

import java.math.BigDecimal

data class ReservationInfo(
    val reservationId: Long,
    val memberId: Long,
    val totalAmount: BigDecimal,
    val status: String,
    val isExpired: Boolean
) {
    fun canPay(): Boolean = status == "PENDING" && !isExpired
}
