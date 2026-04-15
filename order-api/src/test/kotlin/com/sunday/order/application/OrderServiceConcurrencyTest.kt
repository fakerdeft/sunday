package com.sunday.order.application

import com.sunday.order.domain.Product
import com.sunday.order.repository.OrderRepository
import com.sunday.order.repository.ProductRepository
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

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    private val initialStock = 100
    private val threadCount = 5000
    private var productId = 0L

    @BeforeEach
    fun setUp() {
        orderRepository.deleteAll()
        val product = productRepository.save(
            Product(
                id = 0L,
                name = "테스트 상품",
                price = BigDecimal("10000"),
                stock = initialStock,
                totalQuantity = initialStock,
                isHotDeal = false
            )
        )
        productId = product.id
    }

    @Test
    fun `synchronized - 재고보다 많은 동시 요청에서 정확히 재고만큼만 성공`() {
        // given
        val latch = CountDownLatch(1)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when
        val threads = (1..threadCount).map { memberId ->
            Thread {
                try {
                    latch.await()
                    orderService.createOrderWithSynchronized(memberId.toLong(), productId, 1)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                }
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join() }

        // then
        val finalProduct = productRepository.findById(productId)!!
        println("===== 동시성 테스트 결과 =====")
        println("총 요청 수  : $threadCount")
        println("초기 재고   : $initialStock")
        println("성공        : ${successCount.get()}")
        println("실패        : ${failCount.get()}")
        println("최종 재고   : ${finalProduct.stock}")
        println("================================")

        assertThat(successCount.get()).isEqualTo(initialStock)
        assertThat(failCount.get()).isEqualTo(threadCount - initialStock)
        assertThat(finalProduct.stock).isEqualTo(0)
    }
}
