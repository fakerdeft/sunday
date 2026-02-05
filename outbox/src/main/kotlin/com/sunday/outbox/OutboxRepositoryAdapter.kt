package com.sunday.outbox

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
@ConditionalOnProperty(name = ["outbox.enabled"], havingValue = "true", matchIfMissing = true)
class OutboxRepositoryAdapter(
    private val outboxJpaRepository: OutboxJpaRepository
) : OutboxEventRepository {

    override fun save(event: OutboxEvent): OutboxEvent {
        val entity = OutboxJpaEntity.fromDomain(event)
        return outboxJpaRepository.save(entity).toDomain()
    }

    override fun findPendingEventsForPublish(limit: Int): List<OutboxEvent> {
        return outboxJpaRepository.findPendingEventsForPublish(limit)
            .map { it.toDomain() }
    }

    override fun markAsPublished(id: Long) {
        outboxJpaRepository.markAsPublished(id, LocalDateTime.now())
    }

    override fun markAsFailed(id: Long, errorMessage: String) {
        val nextRetryAt = LocalDateTime.now().plusMinutes(1)
        outboxJpaRepository.markAsFailed(id, nextRetryAt, errorMessage)
    }

    override fun deletePublishedEventsBefore(cutoff: LocalDateTime): Int {
        return outboxJpaRepository.deletePublishedEventsBefore(cutoff)
    }
}
