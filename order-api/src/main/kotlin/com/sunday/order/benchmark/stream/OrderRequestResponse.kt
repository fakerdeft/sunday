package com.sunday.order.benchmark.stream

data class OrderRequestResponse(
    val requestId: String,
    val productId: Long,
    val quantity: Int,
    val status: String,
    val reservationId: Long?,
    val failureReason: String?,
    val attempts: Int,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(order: QueuedOrder): OrderRequestResponse = OrderRequestResponse(
            requestId = order.requestId,
            productId = order.productId,
            quantity = order.quantity,
            status = order.status.name,
            reservationId = order.reservationId,
            failureReason = order.failureReason,
            attempts = order.attempts,
            createdAt = order.createdAt.toString(),
            updatedAt = order.updatedAt.toString()
        )
    }
}
