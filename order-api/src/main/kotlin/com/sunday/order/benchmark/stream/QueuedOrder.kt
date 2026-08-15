package com.sunday.order.benchmark.stream

import java.time.Instant

data class QueuedOrder(
    val requestId: String,
    val memberId: Long,
    val productId: Long,
    val quantity: Int,
    val status: OrderQueueStatus,
    val reservationId: Long?,
    val failureReason: String?,
    val attempts: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val retryAt: Instant?
)
