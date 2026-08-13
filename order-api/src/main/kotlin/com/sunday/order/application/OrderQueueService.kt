package com.sunday.order.application

import com.sunday.order.config.OrderQueueProperties
import com.sunday.order.domain.InvalidIdempotencyKeyException
import com.sunday.order.domain.InvalidOrderQuantityException
import com.sunday.order.domain.OrderQueueIdempotencyConflictException
import com.sunday.order.domain.OrderQueueRequestNotFoundException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class OrderQueueService(
    private val redis: StringRedisTemplate,
    private val properties: OrderQueueProperties
) {
    companion object {
        private const val MAX_IDEMPOTENCY_KEY_LENGTH = 100

        private const val FIELD_REQUEST_ID = "requestId"
        private const val FIELD_MEMBER_ID = "memberId"
        private const val FIELD_PRODUCT_ID = "productId"
        private const val FIELD_QUANTITY = "quantity"
        private const val FIELD_STATUS = "status"
        private const val FIELD_RESERVATION_ID = "reservationId"
        private const val FIELD_FAILURE_REASON = "failureReason"
        private const val FIELD_ATTEMPTS = "attempts"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_RETRY_AT = "retryAt"

        private val enqueueScript = DefaultRedisScript(
            """
            local existing = redis.call('GET', KEYS[1])
            if existing then
                return existing
            end

            local idempotencyValue = ARGV[1] .. '|' .. ARGV[3] .. '|' .. ARGV[4]
            redis.call('SET', KEYS[1], idempotencyValue, 'EX', ARGV[6])
            redis.call(
                'HSET', KEYS[2],
                'requestId', ARGV[1],
                'memberId', ARGV[2],
                'productId', ARGV[3],
                'quantity', ARGV[4],
                'status', 'WAITING',
                'reservationId', '',
                'failureReason', '',
                'attempts', '0',
                'createdAt', ARGV[5],
                'updatedAt', ARGV[5],
                'retryAt', ''
            )
            redis.call('EXPIRE', KEYS[2], ARGV[6])
            redis.call(
                'XADD', KEYS[3], '*',
                'requestId', ARGV[1],
                'memberId', ARGV[2],
                'productId', ARGV[3],
                'quantity', ARGV[4]
            )

            return idempotencyValue
            """.trimIndent(),
            String::class.java
        )
    }

    fun enqueue(
        idempotencyKey: String,
        memberId: Long,
        productId: Long,
        quantity: Int
    ): QueuedOrder {
        validateIdempotencyKey(idempotencyKey)
        if (quantity <= 0) {
            throw InvalidOrderQuantityException(quantity)
        }

        val requestId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val result = redis.execute(
            enqueueScript,
            listOf(
                idempotencyRedisKey(memberId, idempotencyKey),
                statusKey(requestId),
                properties.streamKey
            ),
            requestId,
            memberId.toString(),
            productId.toString(),
            quantity.toString(),
            now,
            properties.statusTtl.seconds.toString()
        ) ?: error("Redis가 주문 접수 결과를 반환하지 않았습니다.")
        val values = result.split('|')

        if (values.size != 3) {
            error("Redis 주문 접수 결과 형식이 올바르지 않습니다.")
        }

        val savedRequestId = values[0]
        val savedProductId = values[1].toLong()
        val savedQuantity = values[2].toInt()

        if (savedProductId != productId || savedQuantity != quantity) {
            throw OrderQueueIdempotencyConflictException()
        }

        return find(savedRequestId)
            ?: error("접수된 주문 상태를 Redis에서 찾을 수 없습니다: $savedRequestId")
    }

    fun get(requestId: String, memberId: Long): QueuedOrder {
        validateRequestId(requestId)
        val order = find(requestId) ?: throw OrderQueueRequestNotFoundException(requestId)

        if (order.memberId != memberId) {
            throw OrderQueueRequestNotFoundException(requestId)
        }

        return order
    }

    fun find(requestId: String): QueuedOrder? {
        val fields = redis.opsForHash<String, String>().entries(statusKey(requestId))

        if (fields.isEmpty()) {

            return null
        }

        return QueuedOrder(
            requestId = fields.required(FIELD_REQUEST_ID),
            memberId = fields.required(FIELD_MEMBER_ID).toLong(),
            productId = fields.required(FIELD_PRODUCT_ID).toLong(),
            quantity = fields.required(FIELD_QUANTITY).toInt(),
            status = OrderQueueStatus.valueOf(fields.required(FIELD_STATUS)),
            reservationId = fields.optional(FIELD_RESERVATION_ID)?.toLong(),
            failureReason = fields.optional(FIELD_FAILURE_REASON),
            attempts = fields.required(FIELD_ATTEMPTS).toInt(),
            createdAt = Instant.parse(fields.required(FIELD_CREATED_AT)),
            updatedAt = Instant.parse(fields.required(FIELD_UPDATED_AT)),
            retryAt = fields.optional(FIELD_RETRY_AT)?.let(Instant::parse)
        )
    }

    fun markProcessing(requestId: String, attempt: Int) {
        update(
            requestId,
            mapOf(
                FIELD_STATUS to OrderQueueStatus.PROCESSING.name,
                FIELD_ATTEMPTS to attempt.toString(),
                FIELD_FAILURE_REASON to "",
                FIELD_RETRY_AT to ""
            )
        )
    }

    fun markWaitingForRetry(requestId: String, reason: String?, retryAt: Instant) {
        update(
            requestId,
            mapOf(
                FIELD_STATUS to OrderQueueStatus.WAITING.name,
                FIELD_FAILURE_REASON to reason.orEmpty(),
                FIELD_RETRY_AT to retryAt.toString()
            )
        )
    }

    fun markSucceeded(requestId: String, reservationId: Long) {
        update(
            requestId,
            mapOf(
                FIELD_STATUS to OrderQueueStatus.SUCCEEDED.name,
                FIELD_RESERVATION_ID to reservationId.toString(),
                FIELD_FAILURE_REASON to "",
                FIELD_RETRY_AT to ""
            )
        )
    }

    fun markSoldOut(requestId: String, reason: String?) {
        markTerminal(requestId, OrderQueueStatus.SOLD_OUT, reason)
    }

    fun markRejected(requestId: String, reason: String?) {
        markTerminal(requestId, OrderQueueStatus.REJECTED, reason)
    }

    fun markFailed(requestId: String) {
        markTerminal(
            requestId,
            OrderQueueStatus.FAILED,
            "일시적인 처리 오류가 반복되어 주문 접수에 실패했습니다."
        )
    }

    private fun markTerminal(requestId: String, status: OrderQueueStatus, reason: String?) {
        update(
            requestId,
            mapOf(
                FIELD_STATUS to status.name,
                FIELD_FAILURE_REASON to reason.orEmpty(),
                FIELD_RETRY_AT to ""
            )
        )
    }

    private fun update(requestId: String, values: Map<String, String>) {
        val key = statusKey(requestId)
        val updatedValues = values + (FIELD_UPDATED_AT to Instant.now().toString())

        redis.opsForHash<String, String>().putAll(key, updatedValues)
        redis.expire(key, properties.statusTtl)
    }

    private fun validateIdempotencyKey(idempotencyKey: String) {
        if (idempotencyKey.isBlank() || idempotencyKey.length > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw InvalidIdempotencyKeyException(MAX_IDEMPOTENCY_KEY_LENGTH)
        }
    }

    private fun validateRequestId(requestId: String) {
        try {
            UUID.fromString(requestId)
        } catch (_: IllegalArgumentException) {
            throw OrderQueueRequestNotFoundException(requestId)
        }
    }

    private fun idempotencyRedisKey(memberId: Long, idempotencyKey: String): String =
        "${properties.keyPrefix}:idempotency:$memberId:${sha256(idempotencyKey)}"

    private fun statusKey(requestId: String): String =
        "${properties.keyPrefix}:status:$requestId"

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun Map<String, String>.required(field: String): String =
        this[field] ?: error("Redis 주문 상태에 $field 필드가 없습니다.")

    private fun Map<String, String>.optional(field: String): String? =
        this[field]?.takeIf { it.isNotEmpty() }
}
