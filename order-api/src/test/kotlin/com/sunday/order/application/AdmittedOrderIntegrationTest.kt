package com.sunday.order.application

import com.sunday.common.admission.AdmissionTokenCodec
import com.sunday.order.domain.NotAdmittedException
import com.sunday.order.domain.Product
import com.sunday.order.domain.ProductStock
import com.sunday.order.domain.ReservationStatus
import com.sunday.order.domain.SingleItemOnlyException
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

@SpringBootTest
@Testcontainers
@Sql(scripts = ["classpath:db/order-indexes.sql"])
class AdmittedOrderIntegrationTest {

    companion object {
        private const val SECRET = "test-admission-secret"

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
            registry.add("spring.jpa.properties.hibernate.default_schema") { "order_service" }
            registry.add("sunday.order.admission-secret") { SECRET }
        }
    }

    @Autowired private lateinit var admittedOrderService: AdmittedOrderService
    @Autowired private lateinit var orderService: OrderService
    @Autowired private lateinit var productRepository: ProductRepository
    @Autowired private lateinit var reservationRepository: OrderReservationRepository
    @Autowired private lateinit var productStockRepository: ProductStockRepository

    private val codec = AdmissionTokenCodec(SECRET)
    private var productId = 0L

    @BeforeEach
    fun setUp() {
        val product = productRepository.save(
            Product(
                id = 0L,
                name = "통행증 테스트 상품",
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

    private fun issueToken(memberId: Long): String =
        codec.issue(memberId, productId, Instant.now().plusSeconds(60))

    @Test
    fun `같은 통행증으로 다시 주문하면 기존 예약을 그대로 돌려준다`() {
        saveUnitStocks(3)
        val token = issueToken(101L)

        val first = admittedOrderService.createReservation(101L, productId, 1, token)
        val again = admittedOrderService.createReservation(101L, productId, 1, token)

        assertThat(again.id).isEqualTo(first.id)
        assertThat(reservationRepository.findByMemberId(101L)).hasSize(1)
        // 재고는 한 번만 줄어야 한다.
        assertThat(productStockRepository.countAvailable(productId)).isEqualTo(2L)
    }

    @Test
    fun `통행증이 다르면 서로 다른 예약이 만들어진다`() {
        saveUnitStocks(3)

        val first = admittedOrderService.createReservation(201L, productId, 1, issueToken(201L))
        val second = admittedOrderService.createReservation(202L, productId, 1, issueToken(202L))

        assertThat(second.id).isNotEqualTo(first.id)
        assertThat(productStockRepository.countAvailable(productId)).isEqualTo(1L)
    }

    @Test
    fun `취소된 뒤 같은 통행증으로 요청하면 취소된 예약을 돌려준다`() {
        saveUnitStocks(2)
        val token = issueToken(301L)
        val reservation = admittedOrderService.createReservation(301L, productId, 1, token)

        orderService.cancelReservation(reservation.id)
        val again = admittedOrderService.createReservation(301L, productId, 1, token)

        assertThat(again.id).isEqualTo(reservation.id)
        assertThat(again.status).isEqualTo(ReservationStatus.CANCELLED)
        // 취소로 돌아온 재고가 다시 빠지지 않아야 한다.
        assertThat(productStockRepository.countAvailable(productId)).isEqualTo(2L)
    }

    @Test
    fun `이미 대기 중인 예약이 있으면 새 통행증으로도 중복 주문할 수 없다`() {
        saveUnitStocks(3)
        admittedOrderService.createReservation(401L, productId, 1, issueToken(401L))

        // 같은 회원이 통행증을 새로 받아 와도 대기 중인 예약이 있으면 막힌다.
        assertThatThrownBy {
            admittedOrderService.createReservation(401L, productId, 1, issueToken(401L))
        }.isInstanceOf(com.sunday.order.domain.DuplicatePendingOrderException::class.java)

        assertThat(productStockRepository.countAvailable(productId)).isEqualTo(2L)
    }

    @Test
    fun `수량이 1이 아니면 재고를 건드리지 않고 거절한다`() {
        saveUnitStocks(3)

        assertThatThrownBy {
            admittedOrderService.createReservation(501L, productId, 2, issueToken(501L))
        }.isInstanceOf(SingleItemOnlyException::class.java)

        assertThat(productStockRepository.countAvailable(productId)).isEqualTo(3L)
    }

    @Test
    fun `통행증이 없으면 재고를 건드리지 않고 거절한다`() {
        saveUnitStocks(3)

        assertThatThrownBy {
            admittedOrderService.createReservation(601L, productId, 1, null)
        }.isInstanceOf(NotAdmittedException::class.java)

        assertThat(productStockRepository.countAvailable(productId)).isEqualTo(3L)
    }

    private fun saveUnitStocks(quantity: Int) {
        productStockRepository.saveAll(
            (1..quantity).map { ProductStock(0L, productId, StockStatus.AVAILABLE, 0L, null) }
        )
    }
}
