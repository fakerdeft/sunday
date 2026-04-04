package com.sunday.order.repository

import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderStatus
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
            val ops = redisTemplate.opsForStream<String, String>()
            if (!redisTemplate.hasKey(STREAM_KEY)) {
                val recordId = ops.add(STREAM_KEY, mapOf("init" to "true"))
                if (recordId != null) ops.delete(STREAM_KEY, recordId)
                log.info("스트림 생성 완료: $STREAM_KEY")
            }
        } catch (e: Exception) {
            log.warn("스트림 생성 실패: ${e.message}")
        }
    }

    private fun createConsumerGroupIfNotExists() {
        try {
            redisTemplate.opsForStream<String, String>()
                .createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME)
            log.info("컨슈머 그룹 생성 완료: $GROUP_NAME")
        } catch (e: Exception) {
            log.debug("컨슈머 그룹이 이미 존재함: ${e.message}")
        }
    }

    private fun startListening() {
        val options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
            .builder()
            .pollTimeout(Duration.ofSeconds(1))
            .build()

        listenerContainer = StreamMessageListenerContainer.create(redisTemplate.connectionFactory!!, options)
        subscription = listenerContainer?.receive(
            Consumer.from(GROUP_NAME, CONSUMER_NAME),
            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
            this
        )
        listenerContainer?.start()
        log.info("주문 스트림 컨슈머 시작")
    }

    override fun onMessage(message: MapRecord<String, String, String>) {
        try {
            val data = message.value
            if (data.containsKey("init")) {
                redisTemplate.opsForStream<String, String>().acknowledge(STREAM_KEY, GROUP_NAME, message.id)
                return
            }

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
                log.debug("주문 저장 완료: reservationKey=${order.reservationKey}")
            }

            redisTemplate.opsForStream<String, String>().acknowledge(STREAM_KEY, GROUP_NAME, message.id)
        } catch (e: Exception) {
            log.error("주문 메시지 처리 실패: ${message.id}", e)
        }
    }
}
