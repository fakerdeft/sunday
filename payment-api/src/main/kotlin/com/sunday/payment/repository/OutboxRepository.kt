package com.sunday.payment.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class OutboxRepository(
    private val outboxJpaRepository: OutboxJpaRepository,
    private val queryFactory: JPAQueryFactory
) {
    private val outbox = QOutboxJpaEntity.outboxJpaEntity

    fun save(event: OutboxEvent): OutboxEvent {
        return outboxJpaRepository.save(OutboxJpaEntity.from(event)).toDomain()
    }

    fun findPendingEventsForPublish(limit: Int): List<OutboxEvent> {
        return queryFactory.selectFrom(outbox)
            .where(
                outbox.status.eq(OutboxStatus.PENDING),
                outbox.nextRetryAt.isNull.or(outbox.nextRetryAt.loe(LocalDateTime.now()))
            )
            .orderBy(outbox.createdAt.asc())
            .limit(limit.toLong())
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setHint("jakarta.persistence.lock.timeout", -2)
            .fetch()
            .map { it.toDomain() }
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
