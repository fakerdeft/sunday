package com.sunday.order.benchmark.stream

import com.sunday.order.application.OrderService
import com.sunday.order.domain.OrderQueueIdempotencyConflictException
import com.sunday.order.domain.Product
import com.sunday.order.domain.ProductStock
import com.sunday.order.domain.ReservationStatus
import com.sunday.order.domain.StockStatus
import com.sunday.order.repository.OrderReservationRepository
import com.sunday.order.repository.ProductRepository
import com.sunday.order.repository.ProductStockRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(properties = ["spring.data.redis.repositories.enabled=false"])
@ActiveProfiles("local")
@Testcontainers
@Sql(scripts = ["classpath:db/order-indexes.sql"])
class OrderQueueIntegrationTest {

    companion object {
        private val queueNamespace = UUID.randomUUID().toString()

        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("sunday_test")
            withUsername("sunday")
            withPassword("sunday123")
            withInitScript("db/schema-only.sql")
        }

        @Container
        val redis: GenericContainer<Nothing> = GenericContainer<Nothing>(
            DockerImageName.parse("redis:7-alpine")
        ).apply {
            withExposedPorts(6379)
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create" }
            registry.add("spring.jpa.properties.hibernate.default_schema") { "order_service" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("sunday.order.queue.key-prefix") {
                "sunday:order:{queue-$queueNamespace}"
            }
            registry.add("sunday.order.queue.worker-enabled") { "true" }
            registry.add("sunday.order.queue.poll-delay-ms") { "3600000" }
            registry.add("sunday.order.queue.retry-delay") { "20ms" }
            registry.add("sunday.order.queue.read-block-timeout") { "50ms" }
            registry.add("sunday.order.queue.max-messages-per-cycle") { "100" }
        }
    }

    @Autowired private lateinit var queueService: OrderQueueService
    @Autowired private lateinit var queueWorker: OrderQueueWorker
    @Autowired private lateinit var reservationService: OrderStreamReservationService
    @Autowired private lateinit var orderService: OrderService
    @Autowired private lateinit var productRepository: ProductRepository
    @Autowired private lateinit var reservationRepository: OrderReservationRepository
    @Autowired private lateinit var productStockRepository: ProductStockRepository

    private var productId = 0L

    @BeforeEach
    fun setUp() {
        val product = productRepository.save(
            Product(
                id = 0L,
                name = "대기열 테스트 상품",
                price = BigDecimal("10000"),
                stock = 0,
                totalQuantity = 10,
                isHotDeal = true,
                hotDealStartTime = LocalDateTime.now().minusHours(1),
                hotDealEndTime = LocalDateTime.now().plusHours(1)
            )
        )

        productId = product.id
    }

    @Test
    fun `same idempotency key is queued and processed only once`() {
        saveUnitStocks(1)

        val first = queueService.enqueue("same-order-key", 101L, productId, 1)
        val retry = queueService.enqueue("same-order-key", 101L, productId, 1)
        val completed = awaitStatus(first.requestId, OrderQueueStatus.SUCCEEDED)
        val redelivered = reservationService.create(first.requestId, 101L, productId, 1)

        assertThat(retry.requestId).isEqualTo(first.requestId)
        assertThat(completed.reservationId).isNotNull()
        assertThat(redelivered.id).isEqualTo(completed.reservationId)
        assertThat(reservationRepository.findByMemberId(101L)).hasSize(1)
        assertThat(productStockRepository.countAvailable(productId)).isZero()
    }

    @Test
    fun `same idempotency key rejects a different payload`() {
        saveUnitStocks(2)

        queueService.enqueue("conflicting-order-key", 102L, productId, 1)

        assertThatThrownBy {
            queueService.enqueue("conflicting-order-key", 102L, productId, 2)
        }.isInstanceOf(OrderQueueIdempotencyConflictException::class.java)

        queueWorker.processAvailableMessages()
    }

    @Test
    fun `out of stock request ends without creating a reservation`() {
        val queued = queueService.enqueue("sold-out-order-key", 103L, productId, 1)

        val completed = awaitStatus(queued.requestId, OrderQueueStatus.SOLD_OUT)

        assertThat(completed.reservationId).isNull()
        assertThat(completed.failureReason).contains("재고가 부족")
        assertThat(reservationRepository.findByMemberId(103L)).isEmpty()
    }

    @Test
    fun `queued reservation cancellation restores its claimed unit stock`() {
        saveUnitStocks(1)
        val queued = queueService.enqueue("cancelled-order-key", 104L, productId, 1)
        val completed = awaitStatus(queued.requestId, OrderQueueStatus.SUCCEEDED)

        orderService.cancelReservation(completed.reservationId!!)

        assertThat(productStockRepository.countAvailable(productId)).isEqualTo(1L)
    }

    @Test
    fun `one worker cycle reads and processes multiple queued orders`() {
        saveUnitStocks(3)
        val queuedOrders = (1L..5L).map { memberId ->
            queueService.enqueue("continuous-order-key-$memberId", memberId, productId, 1)
        }

        queueWorker.processAvailableMessages()

        val statuses = queuedOrders.map { queuedOrder ->
            queueService.find(queuedOrder.requestId)?.status
        }
        assertThat(statuses.take(3)).containsOnly(OrderQueueStatus.SUCCEEDED)
        assertThat(statuses.drop(3)).containsOnly(OrderQueueStatus.SOLD_OUT)
        assertThat(
            reservationRepository.countByProductIdAndStatus(productId, ReservationStatus.PENDING)
        ).isEqualTo(3L)
    }

    @Test
    fun `retrying first order blocks later orders to preserve stream order`() {
        saveUnitStocks(1)
        val first = queueService.enqueue("ordered-first-key", 201L, productId, 1)
        val second = queueService.enqueue("ordered-second-key", 202L, productId, 1)

        queueService.markWaitingForRetry(first.requestId, "일시 오류", Instant.now().plusSeconds(10))

        queueWorker.processAvailableMessages()

        assertThat(queueService.find(first.requestId)?.status).isEqualTo(OrderQueueStatus.WAITING)
        assertThat(queueService.find(second.requestId)?.status).isEqualTo(OrderQueueStatus.WAITING)
        assertThat(productStockRepository.countAvailable(productId)).isEqualTo(1L)

        queueService.markWaitingForRetry(first.requestId, "재시도 가능", Instant.now().minusMillis(1))

        queueWorker.processAvailableMessages()

        assertThat(queueService.find(first.requestId)?.status).isEqualTo(OrderQueueStatus.SUCCEEDED)
        assertThat(queueService.find(second.requestId)?.status).isEqualTo(OrderQueueStatus.SOLD_OUT)
        assertThat(reservationRepository.findByMemberId(201L)).hasSize(1)
        assertThat(reservationRepository.findByMemberId(202L)).isEmpty()
    }

    private fun saveUnitStocks(quantity: Int) {
        productStockRepository.saveAll(
            (1..quantity).map { ProductStock(0L, productId, StockStatus.AVAILABLE, 0L, null) }
        )
    }

    private fun awaitStatus(requestId: String, expected: OrderQueueStatus): QueuedOrder {
        val timeoutAt = System.nanoTime() + Duration.ofSeconds(10).toNanos()

        while (System.nanoTime() < timeoutAt) {
            queueWorker.processAvailableMessages()
            val queuedOrder = queueService.find(requestId)

            if (queuedOrder?.status == expected) {

                return queuedOrder
            }

            Thread.sleep(20)
        }

        error("주문 요청 $requestId 이 10초 안에 $expected 상태가 되지 않았습니다.")
    }
}
