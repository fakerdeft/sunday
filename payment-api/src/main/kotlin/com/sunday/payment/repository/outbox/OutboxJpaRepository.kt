package com.sunday.payment.repository.outbox

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OutboxJpaRepository : JpaRepository<OutboxJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        value = """
            SELECT * FROM sunday.outbox
            WHERE status = 'PENDING'
              AND (next_retry_at IS NULL OR next_retry_at <= NOW())
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findPendingEventsForPublish(@Param("limit") limit: Int): List<OutboxJpaEntity>

    @Modifying
    @Query("UPDATE OutboxJpaEntity o SET o.status = 'PUBLISHED', o.publishedAt = :publishedAt WHERE o.id = :id")
    fun markAsPublished(@Param("id") id: Long, @Param("publishedAt") publishedAt: LocalDateTime)

    @Modifying
    @Query("""
        UPDATE OutboxJpaEntity o
        SET o.status = CASE WHEN o.retryCount >= o.maxRetries THEN 'FAILED' ELSE 'PENDING' END,
            o.retryCount = o.retryCount + 1,
            o.nextRetryAt = :nextRetryAt,
            o.errorMessage = :errorMessage
        WHERE o.id = :id
    """)
    fun markAsFailed(
        @Param("id") id: Long,
        @Param("nextRetryAt") nextRetryAt: LocalDateTime?,
        @Param("errorMessage") errorMessage: String?
    )

    @Modifying
    @Query("DELETE FROM OutboxJpaEntity o WHERE o.status = 'PUBLISHED' AND o.publishedAt < :cutoff")
    fun deletePublishedEventsBefore(@Param("cutoff") cutoff: LocalDateTime): Int
}
