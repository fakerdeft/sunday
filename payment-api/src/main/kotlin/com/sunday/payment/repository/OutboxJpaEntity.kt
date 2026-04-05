package com.sunday.payment.repository

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "outbox", schema = "sunday")
class OutboxJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false)
    val aggregateType: OutboxAggregateType,

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    val eventType: OutboxEventType,

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    val payload: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "max_retries", nullable = false)
    val maxRetries: Int = 3,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "published_at")
    var publishedAt: LocalDateTime? = null,

    @Column(name = "next_retry_at")
    var nextRetryAt: LocalDateTime? = null,

    @Column(name = "error_message")
    var errorMessage: String? = null
) {
    fun toDomain(): OutboxEvent = OutboxEvent(
        id = id,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        eventType = eventType,
        payload = payload,
        status = status,
        retryCount = retryCount,
        maxRetries = maxRetries,
        createdAt = createdAt,
        publishedAt = publishedAt,
        nextRetryAt = nextRetryAt,
        errorMessage = errorMessage
    )

    companion object {
        fun fromDomain(event: OutboxEvent): OutboxJpaEntity = OutboxJpaEntity(
            id = event.id,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            eventType = event.eventType,
            payload = event.payload,
            status = event.status,
            retryCount = event.retryCount,
            maxRetries = event.maxRetries,
            createdAt = event.createdAt,
            publishedAt = event.publishedAt,
            nextRetryAt = event.nextRetryAt,
            errorMessage = event.errorMessage
        )
    }
}
