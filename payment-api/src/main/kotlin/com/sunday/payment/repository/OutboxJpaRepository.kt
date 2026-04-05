package com.sunday.payment.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OutboxJpaRepository : JpaRepository<OutboxJpaEntity, Long> {

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
