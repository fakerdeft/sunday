package com.sunday.order.adapter.outbound

import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderStatus
import com.sunday.order.port.outbound.OrderRepository
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamListener
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import org.springframework.data.redis.stream.Subscription
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime

@Component
class OrderStreamConsumer(
    private val redisTemplate: StringRedisTemplate,
    private val orderRepository: OrderRepository,
    private val transactionTemplate: TransactionTemplate
) : StreamListener<String, MapRecord<String, String, String>> {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val STREAM_KEY = RedisOrderStreamPublisher.STREAM_KEY
        const val GROUP_NAME = "order-consumer-group"
        const val CONSUMER_NAME = "order-consumer-1"
    }

    private var listenerContainer: StreamMessageListenerContainer<String, MapRecord<String, String, String>>? = null
    private var subscription: Subscription? = null

    @PostConstruct
    fun init() {
        createStreamIfNotExists()
        createConsumerGroupIfNotExists()
        startListening()
    }

    @PreDestroy
    fun destroy() {
        subscription?.cancel()
        listenerContainer?.stop()
    }

    private fun createStreamIfNotExists() {
        try {
            // 빈 메시지를 추가하고 즉시 삭제하여 Stream 생성
            val ops = redisTemplate.opsForStream<String, String>()
            if (!redisTemplate.hasKey(STREAM_KEY)) {
                val recordId = ops.add(STREAM_KEY, mapOf("init" to "true"))
                if (recordId != null) {
                    ops.delete(STREAM_KEY, recordId)
                }
                log.info("Created stream: $STREAM_KEY")
            }
        } catch (e: Exception) {
            log.warn("Failed to create stream: ${e.message}")
        }
    }

    private fun createConsumerGroupIfNotExists() {
        try {
            redisTemplate.opsForStream<String, String>()
                .createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME)
            log.info("Created consumer group: $GROUP_NAME")
        } catch (e: Exception) {
            // 그룹이 이미 존재하는 경우
            log.debug("Consumer group already exists: ${e.message}")
        }
    }

    private fun startListening() {
        val options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
            .builder()
            .pollTimeout(Duration.ofSeconds(1))
            .build()

        listenerContainer = StreamMessageListenerContainer.create(
            redisTemplate.connectionFactory!!,
            options
        )

        subscription = listenerContainer?.receive(
            Consumer.from(GROUP_NAME, CONSUMER_NAME),
            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
            this
        )

        listenerContainer?.start()
        log.info("Order stream consumer started")
    }

    override fun onMessage(message: MapRecord<String, String, String>) {
        try {
            val data = message.value

            // init 메시지는 무시
            if (data.containsKey("init")) {
                redisTemplate.opsForStream<String, String>()
                    .acknowledge(STREAM_KEY, GROUP_NAME, message.id)
                return
            }

            // TransactionTemplate으로 트랜잭션 보장
            transactionTemplate.execute {
                val order = Order(
                    id = 0L,
                    memberId = data["memberId"]!!.toLong(),
                    productId = data["productId"]!!.toLong(),
                    productName = data["productName"]!!,
                    quantity = data["quantity"]!!.toInt(),
                    unitPrice = BigDecimal(data["unitPrice"]!!),
                    totalAmount = BigDecimal(data["totalAmount"]!!),
                    status = OrderStatus.PENDING,
                    reservationKey = data["reservationKey"]!!,
                    expireAt = LocalDateTime.now().plusMinutes(5),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )

                orderRepository.save(order)
                log.debug("Order saved successfully: reservationKey=${order.reservationKey}")
            }

            // ACK 처리 (트랜잭션 성공 후)
            redisTemplate.opsForStream<String, String>()
                .acknowledge(STREAM_KEY, GROUP_NAME, message.id)

        } catch (e: Exception) {
            log.error("Failed to process order message: ${message.id}", e)
            // 실패한 메시지는 ACK하지 않아 재처리됨
        }
    }
}
