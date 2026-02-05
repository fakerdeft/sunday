package com.sunday.outbox

import java.time.LocalDateTime

data class OutboxEvent(
    val id: Long = 0,
    val aggregateType: AggregateType,
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
            aggregateType: AggregateType,
            aggregateId: Long,
            eventType: OutboxEventType,
            payload: String
        ): OutboxEvent {
            return OutboxEvent(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = payload
            )
        }
    }

    fun markAsPublished(): OutboxEvent {
        return copy(
            status = OutboxStatus.PUBLISHED,
            publishedAt = LocalDateTime.now()
        )
    }

    fun markAsFailed(error: String): OutboxEvent {
        val nextRetry = if (retryCount < maxRetries) {
            val delayMinutes = (1 shl retryCount).toLong()
            LocalDateTime.now().plusMinutes(delayMinutes)
        } else {
            null
        }

        return copy(
            status = if (retryCount >= maxRetries) OutboxStatus.FAILED else OutboxStatus.PENDING,
            retryCount = retryCount + 1,
            nextRetryAt = nextRetry,
            errorMessage = error
        )
    }
}
