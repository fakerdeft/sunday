package com.sunday.order.application

import com.sunday.order.domain.Product
import com.sunday.order.domain.ProductStock
import com.sunday.order.domain.OrderStatus
import com.sunday.order.domain.ReservationStatus
import com.sunday.order.domain.StockStatus
import com.sunday.order.repository.OrderRepository
import com.sunday.order.repository.OrderReservationRepository
import com.sunday.order.repository.ProductRepository
import com.sunday.order.repository.ProductStockRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
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

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create" }
            registry.add("spring.jpa.properties.hibernate.default_schema") { "sunday" }
        }
    }

    @Autowired private lateinit var orderService: OrderService
    @Autowired private lateinit var orderRepository: OrderRepository
    @Autowired private lateinit var productRepository: ProductRepository
    @Autowired private lateinit var reservationRepository: OrderReservationRepository
    @Autowired private lateinit var productStockRepository: ProductStockRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val initialStock = 100
    private val threadCount = 500
    private var productId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_reservations_pending_member_product
            ON sunday.order_reservations(member_id, product_id)
            WHERE status = 'PENDING'
            """.trimIndent()
        )
        orderRepository.deleteAll()
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
    fun `skipLocked - 재고보다 많은 동시 요청에서 정확히 재고만큼만 성공`() {
        val stocks = (1..initialStock).map { ProductStock(0L, productId, StockStatus.AVAILABLE, 0L, null) }

        productStockRepository.saveAll(stocks)

        val (success, fail) = runConcurrent { memberId ->
            orderService.createReservation(memberId, productId, 1)
        }

        val remaining = productStockRepository.countAvailable(productId)

        printResult("SkipLocked", success, fail, remaining.toInt())

        assertThat(success).isEqualTo(initialStock)
        assertThat(remaining).isEqualTo(0L)
    }

    @Test
    fun `confirmed reservation is not expired and its stock stays claimed`() {
        saveUnitStocks(1)
        val reservation = orderService.createReservation(1L, productId, 1)

        val order = orderService.confirmReservation(reservation.id)
        val confirmed = reservationRepository.findById(reservation.id)!!

        reservationRepository.saveAndFlush(confirmed.copy(expireAt = LocalDateTime.now().minusMinutes(1)))

        val expiredCount = orderService.expireReservations()

        assertThat(order.reservationId).isEqualTo(reservation.id)
        assertThat(expiredCount).isZero()
        assertThat(reservationRepository.findById(reservation.id)!!.status)
            .isEqualTo(ReservationStatus.CONFIRMED)
        assertThat(productStockRepository.countClaimedByReservationId(reservation.id)).isEqualTo(1L)
        assertThat(productStockRepository.countAvailable(productId)).isZero()
    }

    @Test
    fun `order confirmation and cancellation retries are idempotent`() {
        saveUnitStocks(1)
        val reservation = orderService.createReservation(2L, productId, 1)

        val firstConfirmation = orderService.confirmReservation(reservation.id)
        val confirmationRetry = orderService.confirmReservation(reservation.id)

        orderService.cancelOrder(reservation.id)
        orderService.cancelOrder(reservation.id)

        assertThat(confirmationRetry.reservationId).isEqualTo(firstConfirmation.reservationId)
        assertThat(confirmationRetry.status).isEqualTo(OrderStatus.PAID)
        assertThat(orderRepository.findByReservationId(reservation.id)!!.status)
            .isEqualTo(OrderStatus.CANCELLED)
        assertThat(productStockRepository.countClaimedByReservationId(reservation.id)).isEqualTo(1L)
        assertThat(productStockRepository.countAvailable(productId)).isZero()
    }

    @Test
    fun `cancellation releases only stock owned by that reservation`() {
        val memberId = 7L
        val unrelatedReservationId = 999_999L

        productStockRepository.saveAll(
            listOf(
                ProductStock(0L, productId, StockStatus.AVAILABLE, 0L, null),
                ProductStock(0L, productId, StockStatus.AVAILABLE, 0L, null),
                ProductStock(
                    id = 0L,
                    productId = productId,
                    status = StockStatus.SOLD,
                    version = 0L,
                    reservedBy = memberId,
                    reservationId = unrelatedReservationId
                )
            )
        )
        val reservation = orderService.createReservation(memberId, productId, 2)

        val cancelled = orderService.cancelReservation(reservation.id)
        val retried = orderService.cancelReservation(reservation.id)

        assertThat(cancelled.status).isEqualTo(ReservationStatus.CANCELLED)
        assertThat(retried.status).isEqualTo(ReservationStatus.CANCELLED)
        assertThat(productStockRepository.countClaimedByReservationId(reservation.id)).isZero()
        assertThat(productStockRepository.countClaimedByReservationId(unrelatedReservationId)).isEqualTo(1L)
        assertThat(productStockRepository.countAvailable(productId)).isEqualTo(2L)
    }

    @Test
    fun `confirm and cancel race has exactly one consistent winner`() {
        saveUnitStocks(1)
        val reservation = orderService.createReservation(11L, productId, 1)
        val start = CountDownLatch(1)
        val completed = CopyOnWriteArrayList<String>()

        val confirm = Thread {
            try {
                start.await()
                orderService.confirmReservation(reservation.id)
                completed += "confirm"
            } catch (_: Exception) {
            }
        }
        val cancel = Thread {
            try {
                start.await()
                orderService.cancelReservation(reservation.id)
                completed += "cancel"
            } catch (_: Exception) {
            }
        }

        confirm.start()
        cancel.start()
        start.countDown()
        confirm.join()
        cancel.join()

        val finalReservation = reservationRepository.findById(reservation.id)!!

        assertThat(completed).hasSize(1)
        when (completed.single()) {
            "confirm" -> {
                assertThat(finalReservation.status).isEqualTo(ReservationStatus.CONFIRMED)
                assertThat(orderRepository.findByReservationId(reservation.id)).isNotNull()
                assertThat(productStockRepository.countAvailable(productId)).isZero()
            }

            "cancel" -> {
                assertThat(finalReservation.status).isEqualTo(ReservationStatus.CANCELLED)
                assertThat(orderRepository.findByReservationId(reservation.id)).isNull()
                assertThat(productStockRepository.countAvailable(productId)).isEqualTo(1L)
            }
        }
    }

    @Test
    fun `expired pending reservation restores its exact stock`() {
        saveUnitStocks(2)
        val reservation = orderService.createReservation(21L, productId, 2)

        reservationRepository.saveAndFlush(reservation.copy(expireAt = LocalDateTime.now().minusSeconds(1)))

        val expiredCount = orderService.expireReservations()

        assertThat(expiredCount).isEqualTo(1)
        assertThat(reservationRepository.findById(reservation.id)!!.status).isEqualTo(ReservationStatus.EXPIRED)
        assertThat(productStockRepository.countClaimedByReservationId(reservation.id)).isZero()
        assertThat(productStockRepository.countAvailable(productId)).isEqualTo(2L)
    }

    @Test
    fun `product query reports unit stock availability instead of benchmark stock column`() {
        saveUnitStocks(2)

        val reservation = orderService.createReservation(22L, productId, 1)

        assertThat(orderService.getProduct(productId).availableStock).isEqualTo(1L)
        assertThat(orderService.getProducts().single { it.product.id == productId }.availableStock).isEqualTo(1L)
        orderService.cancelReservation(reservation.id)
        assertThat(orderService.getProduct(productId).availableStock).isEqualTo(2L)
    }

    @Test
    fun `database constraint allows only one pending reservation per member and product`() {
        saveUnitStocks(10)
        val start = CountDownLatch(1)
        val success = AtomicInteger(0)
        val threads = (1..30).map {
            Thread {
                try {
                    start.await()
                    orderService.createReservation(31L, productId, 1)
                    success.incrementAndGet()
                } catch (_: Exception) {
                }
            }
        }

        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join() }

        assertThat(success.get()).isEqualTo(1)
        assertThat(reservationRepository.findByMemberId(31L).count { it.status == ReservationStatus.PENDING })
            .isEqualTo(1)
        assertThat(productStockRepository.countAvailable(productId)).isEqualTo(9L)
    }

    private fun saveUnitStocks(quantity: Int) {
        productStockRepository.saveAll(
            (1..quantity).map { ProductStock(0L, productId, StockStatus.AVAILABLE, 0L, null) }
        )
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
