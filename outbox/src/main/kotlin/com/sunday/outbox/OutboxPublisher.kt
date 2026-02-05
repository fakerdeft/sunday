package com.sunday.outbox

import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StringRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@ConditionalOnProperty(name = ["outbox.publisher.enabled"], havingValue = "true", matchIfMissing = true)
class OutboxPublisher(
    private val outboxRepository: OutboxEventRepository,
    private val redisTemplate: StringRedisTemplate,
    @Value("\${outbox.publisher.batch-size:100}") private val batchSize: Int,
    @Value("\${outbox.publisher.stream-key:payment:stream}") private val streamKey: String
) {
    private val log = LogManager.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${outbox.publisher.poll-interval-ms:100}")
    @Transactional
    fun publishPendingEvents() {
        val events = outboxRepository.findPendingEventsForPublish(batchSize)

        if (events.isEmpty()) return

        log.debug("대기 중인 Outbox 이벤트 ${events.size}건 발행 시작")

        events.forEach { event ->
            try {
                publishToRedisStream(event)
                outboxRepository.markAsPublished(event.id)
                log.info("Outbox 이벤트 발행 완료: id=${event.id}, type=${event.eventType}")
            } catch (e: Exception) {
                log.error("Outbox 이벤트 발행 실패: id=${event.id}", e)
                outboxRepository.markAsFailed(event.id, e.message ?: "알 수 없는 오류")
            }
        }
    }

    private fun publishToRedisStream(event: OutboxEvent) {
        val message = mapOf(
            "eventType" to event.eventType.name,
            "aggregateType" to event.aggregateType.name,
            "aggregateId" to event.aggregateId.toString(),
            "payload" to event.payload
        )

        val record = StringRecord.of(message).withStreamKey(streamKey)
        val recordId: RecordId? = redisTemplate.opsForStream<String, String>().add(record)

        log.debug("Redis Stream 발행 완료: key=$streamKey, recordId=$recordId")
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanupOldEvents() {
        val cutoff = LocalDateTime.now().minusDays(7)
        val deletedCount = outboxRepository.deletePublishedEventsBefore(cutoff)
        log.info("오래된 Outbox 이벤트 ${deletedCount}건 정리 완료 (발행일: $cutoff 이전)")
    }
}
