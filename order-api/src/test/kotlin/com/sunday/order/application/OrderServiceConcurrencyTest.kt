package com.sunday.order.application

import com.sunday.order.domain.Product
import com.sunday.order.domain.ProductStock
import com.sunday.order.domain.StockStatus
import com.sunday.order.repository.OrderReservationRepository
import com.sunday.order.repository.ProductRepository
import com.sunday.order.repository.ProductStockRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@Testcontainers
class OrderServiceConcurrencyTest {

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("sunday_test")
            withUsername("sunday")
            withPassword("sunday123")
            withInitScript("db/schema-only.sql")
        }

        @Container
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").apply {
            withExposedPorts(6379)
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create" }
            registry.add("spring.jpa.properties.hibernate.default_schema") { "sunday" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Autowired private lateinit var orderService: OrderService
    @Autowired private lateinit var productRepository: ProductRepository
    @Autowired private lateinit var reservationRepository: OrderReservationRepository
    @Autowired private lateinit var productStockRepository: ProductStockRepository
    @Autowired private lateinit var stockCasManager: StockCasManager
    @Autowired private lateinit var redisTokenQueueManager: RedisTokenQueueManager

    private val initialStock = 100
    private val threadCount = 500
    private var productId = 0L

    @BeforeEach
    fun setUp() {
        reservationRepository.deleteAll()
        val product = productRepository.save(
            Product(
                id = 0L,
                name = "테스트 상품",
                price = BigDecimal("10000"),
                stock = initialStock,
                totalQuantity = initialStock,
                isHotDeal = true,
                hotDealStartTime = LocalDateTime.now().minusHours(1),
                hotDealEndTime = LocalDateTime.now().plusHours(1)
            )
        )
        productId = product.id
    }

    @Test
    fun `reentrantLock - 재고보다 많은 동시 요청에서 정확히 재고만큼만 성공`() {
        val (success, fail) = runConcurrent { memberId ->
            orderService.createReservationWithReentrantLock(memberId, productId, 1)
        }

        val finalStock = productRepository.findById(productId)!!.stock
        printResult("ReentrantLock", success, fail, finalStock)

        assertThat(success).isEqualTo(initialStock)
        assertThat(finalStock).isEqualTo(0)
    }

    @Test
    fun `pessimisticLock - 재고보다 많은 동시 요청에서 정확히 재고만큼만 성공`() {
        val (success, fail) = runConcurrent { memberId ->
            orderService.createReservationWithPessimisticLock(memberId, productId, 1)
        }

        val finalStock = productRepository.findById(productId)!!.stock
        printResult("PessimisticLock", success, fail, finalStock)

        assertThat(success).isEqualTo(initialStock)
        assertThat(finalStock).isEqualTo(0)
    }

    @Test
    fun `distributedLock - 재고보다 많은 동시 요청에서 정확히 재고만큼만 성공`() {
        val (success, fail) = runConcurrent { memberId ->
            orderService.createReservationWithDistributedLock(memberId, productId, 1)
        }

        val finalStock = productRepository.findById(productId)!!.stock
        printResult("DistributedLock", success, fail, finalStock)

        assertThat(success).isEqualTo(initialStock)
        assertThat(finalStock).isEqualTo(0)
    }

    @Test
    fun `skipLocked - 재고보다 많은 동시 요청에서 정확히 재고만큼만 성공`() {
        val stocks = (1..initialStock).map { ProductStock(0L, productId, StockStatus.AVAILABLE, 0L, null) }
        productStockRepository.saveAll(stocks)

        val (success, fail) = runConcurrent { memberId ->
            orderService.createReservationWithSkipLocked(memberId, productId, 1)
        }

        val remaining = productStockRepository.countAvailable(productId)
        printResult("SkipLocked", success, fail, remaining.toInt())

        assertThat(success).isEqualTo(initialStock)
        assertThat(remaining).isEqualTo(0L)
    }

    @Test
    fun `cas - 재고보다 많은 동시 요청에서 정확히 재고만큼만 성공`() {
        stockCasManager.reset(productId, initialStock)

        val (success, fail) = runConcurrent { memberId ->
            orderService.createReservationWithCas(memberId, productId, 1)
        }

        val finalStock = productRepository.findById(productId)!!.stock
        printResult("CAS", success, fail, finalStock)

        assertThat(success).isEqualTo(initialStock)
    }

    @Test
    fun `redisQueue - 재고보다 많은 동시 요청에서 정확히 재고만큼만 성공`() {
        redisTokenQueueManager.initQueue(productId, initialStock)

        val (success, fail) = runConcurrent { memberId ->
            orderService.createReservationWithRedisQueue(memberId, productId, 1)
        }

        val remaining = redisTokenQueueManager.queueSize(productId)
        printResult("RedisQueue", success, fail, remaining.toInt())

        assertThat(success).isEqualTo(initialStock)
        assertThat(remaining).isEqualTo(0L)
    }

    private fun runConcurrent(action: (Long) -> Unit): Pair<Int, Int> {
        val latch = CountDownLatch(1)
        val success = AtomicInteger(0)
        val fail = AtomicInteger(0)

        val threads = (1..threadCount).map { memberId ->
            Thread {
                try {
                    latch.await()
                    action(memberId.toLong())
                    success.incrementAndGet()
                } catch (e: Exception) {
                    fail.incrementAndGet()
                }
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join() }

        return success.get() to fail.get()
    }

    private fun printResult(method: String, success: Int, fail: Int, remaining: Int) {
        println("===== [$method] 동시성 테스트 결과 =====")
        println("총 요청 수  : $threadCount")
        println("초기 재고   : $initialStock")
        println("성공        : $success")
        println("실패        : $fail")
        println("남은 재고   : $remaining")
        println("==========================================")
    }
}
