package com.sunday.outbox

import java.time.LocalDateTime

interface OutboxEventRepository {
    fun save(event: OutboxEvent): OutboxEvent
    fun findPendingEventsForPublish(limit: Int): List<OutboxEvent>
    fun markAsPublished(id: Long)
    fun markAsFailed(id: Long, errorMessage: String)
    fun deletePublishedEventsBefore(cutoff: LocalDateTime): Int
}
