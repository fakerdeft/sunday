package com.sunday.gate.api.dto

import com.sunday.gate.application.OrderPass
import com.sunday.gate.application.OrderPassStatus
import java.time.Instant

data class OrderPassResponse(
    val productId: Long,
    val status: OrderPassStatus,

    val token: String?,
    val expiresAt: Instant?,
    val canOrder: Boolean
) {
    companion object {
        fun from(pass: OrderPass): OrderPassResponse = OrderPassResponse(
            productId = pass.productId,
            status = pass.status,
            token = pass.token,
            expiresAt = pass.expiresAt,
            canOrder = pass.canOrder()
        )
    }
}
