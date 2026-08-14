package com.sunday.order.application

import com.sunday.order.config.OrderQueueProperties
import com.sunday.order.domain.OrderException
import com.sunday.order.domain.OutOfStockException
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
@ConditionalOnProperty(
    prefix = "sunday.order.queue",
    name = ["worker-enabled"],
    havingValue = "true"
)
class OrderQueueWorker(
    private val redis: StringRedisTemplate,
    private val properties: OrderQueueProperties,
    private val queueService: OrderQueueService,
    private val orderService: OrderService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val stream by lazy { redis.opsForStream<String, String>() }
    private val consumer by lazy { Consumer.from(properties.consumerGroup, properties.consumerName) }

    @PostConstruct
    fun createConsumerGroup() {
        try {
            stream.createGroup(
                properties.streamKey,
                ReadOffset.from("0-0"),
                properties.consumerGroup
            )
        } catch (e: DataAccessException) {
            if (!isExistingGroup(e)) {
                throw e
            }
        }
    }

    @Scheduled(fixedDelayString = "\${sunday.order.queue.poll-delay-ms:100}")
    fun processAvailableMessages() {
        try {
            val pendingRecords = read(
                offset = ReadOffset.from("0-0"),
                count = properties.maxMessagesPerCycle
            )
            val pendingCompleted = processRecordsInOrder(pendingRecords)

            if (!pendingCompleted) {
                return
            }

            val remainingCount = properties.maxMessagesPerCycle - pendingRecords.size

            if (remainingCount > 0) {
                val newRecords = read(
                    offset = ReadOffset.lastConsumed(),
                    count = remainingCount,
                    blockTimeout = properties.readBlockTimeout.takeIf { pendingRecords.isEmpty() }
                )
                processRecordsInOrder(newRecords)
            }
        } catch (e: Exception) {
            log.error("주문 대기열 처리 주기에 실패했습니다", e)
        }
    }

    private fun read(
        offset: ReadOffset,
        count: Int,
        blockTimeout: Duration? = null
    ): List<MapRecord<String, String, String>> {
        var options = StreamReadOptions.empty().count(count.toLong())

        if (blockTimeout != null) {
            options = options.block(blockTimeout)
        }

        return stream.read(
            consumer,
            options,
            StreamOffset.create(properties.streamKey, offset)
        )
    }

    private fun processRecordsInOrder(records: List<MapRecord<String, String, String>>): Boolean {
        for (record in records) {
            if (!processRecord(record)) {
                return false
            }
        }

        return true
    }

    private fun processRecord(record: MapRecord<String, String, String>): Boolean {
        val requestId = record.value["requestId"]

        if (requestId == null) {
            log.error("형식이 잘못된 주문 대기열 메시지를 폐기합니다: recordId={}", record.id)
            acknowledge(record)

            return true
        }

        val queuedOrder = queueService.find(requestId)

        if (queuedOrder == null) {
            log.error("상태 정보가 없는 주문 대기열 메시지를 폐기합니다: requestId={}", requestId)
            acknowledge(record)

            return true
        }

        if (queuedOrder.status.isTerminal()) {
            acknowledge(record)

            return true
        }

        if (queuedOrder.retryAt?.isAfter(Instant.now()) == true) {
            return false
        }

        val attempt = queuedOrder.attempts + 1

        queueService.markProcessing(requestId, attempt)

        try {
            val reservation = orderService.createQueuedReservation(
                requestId = requestId,
                memberId = queuedOrder.memberId,
                productId = queuedOrder.productId,
                quantity = queuedOrder.quantity
            )

            queueService.markSucceeded(requestId, reservation.id)
            acknowledge(record)

            return true
        } catch (e: OutOfStockException) {
            queueService.markSoldOut(requestId, e.message)
            acknowledge(record)

            return true
        } catch (e: OrderException) {
            queueService.markRejected(requestId, e.message)
            acknowledge(record)

            return true
        } catch (e: Exception) {
            return handleUnexpectedFailure(record, requestId, attempt, e)
        }
    }

    private fun handleUnexpectedFailure(
        record: MapRecord<String, String, String>,
        requestId: String,
        attempt: Int,
        error: Exception
    ): Boolean {
        log.error("주문 대기열 처리에 실패했습니다: requestId={}, 시도={}", requestId, attempt, error)

        if (attempt >= properties.maxAttempts) {
            queueService.markFailed(requestId)
            acknowledge(record)

            return true
        }

        queueService.markWaitingForRetry(
            requestId,
            "주문 처리 중 일시적인 오류가 발생해 재시도합니다.",
            Instant.now().plus(properties.retryDelay)
        )

        return false
    }

    private fun acknowledge(record: MapRecord<String, String, String>) {
        val acknowledged = stream.acknowledge(properties.streamKey, properties.consumerGroup, record.id)

        if (acknowledged == 1L) {
            stream.delete(properties.streamKey, record.id)
        }
    }

    private fun isExistingGroup(error: Throwable): Boolean =
        generateSequence(error) { it.cause }
            .any { it.message?.contains("BUSYGROUP") == true }
}
