package com.sunday.payment.repository.outbox

import java.time.LocalDateTime

data class OutboxEvent(
    val id: Long = 0,
    val aggregateType: OutboxAggregateType,
    val aggregateId: Long,
    val eventType: OutboxEventType,
    val payload: String,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val publishedAt: LocalDateTime? = null,
    val nextRetryAt: LocalDateTime? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun create(
            aggregateType: OutboxAggregateType,
            aggregateId: Long,
            eventType: OutboxEventType,
            payload: String
        ): OutboxEvent = OutboxEvent(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload
        )
    }
}
