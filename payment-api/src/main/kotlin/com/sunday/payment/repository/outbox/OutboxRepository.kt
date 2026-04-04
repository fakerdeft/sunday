package com.sunday.payment.repository.outbox

import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class OutboxRepository(
    private val outboxJpaRepository: OutboxJpaRepository
) {

    fun save(event: OutboxEvent): OutboxEvent {
        return outboxJpaRepository.save(OutboxJpaEntity.fromDomain(event)).toDomain()
    }

    fun findPendingEventsForPublish(limit: Int): List<OutboxEvent> {
        return outboxJpaRepository.findPendingEventsForPublish(limit).map { it.toDomain() }
    }

    fun markAsPublished(id: Long) {
        outboxJpaRepository.markAsPublished(id, LocalDateTime.now())
    }

    fun markAsFailed(id: Long, errorMessage: String) {
        val nextRetryAt = LocalDateTime.now().plusMinutes(1)
        outboxJpaRepository.markAsFailed(id, nextRetryAt, errorMessage)
    }

    fun deletePublishedEventsBefore(cutoff: LocalDateTime): Int {
        return outboxJpaRepository.deletePublishedEventsBefore(cutoff)
    }
}
