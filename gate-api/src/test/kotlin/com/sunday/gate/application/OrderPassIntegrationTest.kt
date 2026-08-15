package com.sunday.gate.application

import com.sunday.common.admission.AdmissionTokenCodec
import com.sunday.common.admission.AdmissionTokenResult
import com.sunday.gate.client.OrderApiClient
import com.sunday.gate.client.ProductStockSnapshot
import com.sunday.gate.config.scheduler.StockSyncScheduler
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.UUID

@SpringBootTest
@Testcontainers
class OrderPassIntegrationTest {

    companion object {
        private val namespace = UUID.randomUUID().toString()
        private const val PRODUCT_ID = 1L

        @Container
        val redis: GenericContainer<Nothing> = GenericContainer<Nothing>(
            DockerImageName.parse("redis:7-alpine")
        ).apply {
            withExposedPorts(6379)
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("clients.order-api.url") { "http://localhost:0" }

            registry.add("sunday.order-pass.admission-secret") { "test-admission-secret" }
            registry.add("sunday.order-pass.key-prefix") { "sunday:pass-$namespace" }
            registry.add("sunday.order-pass.pass-ttl") { "400ms" }
            registry.add("sunday.order-pass.budget-ttl") { "60s" }
            registry.add("sunday.order-pass.managed-product-ids") { "$PRODUCT_ID" }

            // 재고 동기화는 테스트에서 직접 호출하므로 스케줄러는 사실상 멈춰 둔다.
            registry.add("sunday.order-pass.sync-delay-ms") { "3600000" }
        }
    }

    @TestConfiguration
    class StubOrderApi {
        @Bean
        @Primary
        fun orderApiClient(): OrderApiClient = mockk()
    }

    @Autowired private lateinit var orderPassService: OrderPassService
    @Autowired private lateinit var scheduler: StockSyncScheduler
    @Autowired private lateinit var orderApiClient: OrderApiClient
    @Autowired private lateinit var tokenCodec: AdmissionTokenCodec

    @BeforeEach
    fun setUp() {
        orderPassService.reset(PRODUCT_ID)
    }

    /** 주문 서버의 재고를 이 값으로 두고 한 주기를 돌린다. */
    private fun syncWithStock(available: Long) {
        every { orderApiClient.getStockSnapshot(PRODUCT_ID) } returns
            ProductStockSnapshot(PRODUCT_ID, available)
        scheduler.sync(PRODUCT_ID)
    }

    @Test
    fun `재고가 남아 있으면 통과시키고 증표를 발급한다`() {
        syncWithStock(available = 3)

        val pass = orderPassService.requestPass(PRODUCT_ID, 101L)

        assertThat(pass.status).isEqualTo(OrderPassStatus.PASSED)
        assertThat(tokenCodec.verify(pass.token, 101L, PRODUCT_ID)).isEqualTo(AdmissionTokenResult.VALID)
    }

    @Test
    fun `통행증은 재고 수만큼만 나가고 나머지는 즉시 품절이다`() {
        syncWithStock(available = 3)

        val passes = (1L..20L).map { offset -> orderPassService.requestPass(PRODUCT_ID, 200L + offset) }

        assertThat(passes.count { it.status == OrderPassStatus.PASSED }).isEqualTo(3)
        assertThat(passes.count { it.status == OrderPassStatus.SOLD_OUT }).isEqualTo(17)
        // 앞에서 요청한 순서대로 통과한다.
        assertThat(passes.take(3).map { it.status }).containsOnly(OrderPassStatus.PASSED)
    }

    @Test
    fun `주문이 진행되는 동안 동기화가 없어도 통행증이 더 나가지 않는다`() {
        syncWithStock(available = 3)

        val first = (1L..3L).map { offset -> orderPassService.requestPass(PRODUCT_ID, 300L + offset) }
        assertThat(first.map { it.status }).containsOnly(OrderPassStatus.PASSED)

        // 세 명이 주문을 마치고 통행증을 반납했다. 다음 동기화 전까지는 추가 발급이 없어야 한다.
        (1L..3L).forEach { offset -> orderPassService.release(PRODUCT_ID, 300L + offset) }

        assertThat(orderPassService.requestPass(PRODUCT_ID, 399L).status)
            .isEqualTo(OrderPassStatus.SOLD_OUT)
    }

    @Test
    fun `재고가 없으면 주문 서버를 부르지 않고 품절로 끝낸다`() {
        syncWithStock(available = 0)

        val pass = orderPassService.requestPass(PRODUCT_ID, 401L)

        assertThat(pass.status).isEqualTo(OrderPassStatus.SOLD_OUT)
        assertThat(pass.token).isNull()
    }

    @Test
    fun `같은 회원이 다시 요청하면 같은 통행증을 돌려준다`() {
        syncWithStock(available = 1)

        val first = orderPassService.requestPass(PRODUCT_ID, 501L)
        val again = orderPassService.requestPass(PRODUCT_ID, 501L)

        assertThat(again.status).isEqualTo(OrderPassStatus.PASSED)
        assertThat(again.expiresAt).isEqualTo(first.expiresAt)
        assertThat(orderPassService.inFlightCount(PRODUCT_ID)).isEqualTo(1)
    }

    @Test
    fun `증표는 다른 회원이나 다른 상품에 쓸 수 없다`() {
        syncWithStock(available = 1)

        val token = orderPassService.requestPass(PRODUCT_ID, 601L).token

        assertThat(tokenCodec.verify(token, 602L, PRODUCT_ID)).isEqualTo(AdmissionTokenResult.MISMATCHED)
        assertThat(tokenCodec.verify(token, 601L, 99L)).isEqualTo(AdmissionTokenResult.MISMATCHED)
    }

    @Test
    fun `주문이 성공하면 재고가 줄어 다음 사람은 통과하지 못한다`() {
        syncWithStock(available = 1)
        assertThat(orderPassService.requestPass(PRODUCT_ID, 701L).status).isEqualTo(OrderPassStatus.PASSED)
        orderPassService.release(PRODUCT_ID, 701L)

        // 주문이 커밋되어 재고가 0이 됐다.
        syncWithStock(available = 0)

        assertThat(orderPassService.requestPass(PRODUCT_ID, 702L).status).isEqualTo(OrderPassStatus.SOLD_OUT)
    }

    @Test
    fun `결제 실패로 재고가 돌아오면 다시 통과시킨다`() {
        syncWithStock(available = 0)
        assertThat(orderPassService.requestPass(PRODUCT_ID, 801L).status).isEqualTo(OrderPassStatus.SOLD_OUT)

        // 결제 실패나 예약 만료로 재고가 돌아왔다.
        syncWithStock(available = 1)

        assertThat(orderPassService.requestPass(PRODUCT_ID, 801L).status).isEqualTo(OrderPassStatus.PASSED)
    }

    @Test
    fun `통행증을 받고 이탈하면 만료 후 다음 사람에게 돌아간다`() {
        syncWithStock(available = 1)
        assertThat(orderPassService.requestPass(PRODUCT_ID, 901L).status).isEqualTo(OrderPassStatus.PASSED)
        assertThat(orderPassService.requestPass(PRODUCT_ID, 902L).status).isEqualTo(OrderPassStatus.SOLD_OUT)

        // 901번이 주문하지 않고 이탈했다. 재고는 그대로 1이다.
        Thread.sleep(500)
        syncWithStock(available = 1)

        assertThat(orderPassService.requestPass(PRODUCT_ID, 902L).status).isEqualTo(OrderPassStatus.PASSED)
    }

    @Test
    fun `발급했지만 주문하지 않은 통행증은 재고를 잡고 있는 것으로 본다`() {
        syncWithStock(available = 2)

        orderPassService.requestPass(PRODUCT_ID, 1001L)
        orderPassService.requestPass(PRODUCT_ID, 1002L)

        // 두 명 모두 아직 주문 전이라 재고는 그대로 2이지만 통행증은 더 나가지 않는다.
        syncWithStock(available = 2)

        assertThat(orderPassService.budget(PRODUCT_ID)).isZero()
        assertThat(orderPassService.requestPass(PRODUCT_ID, 1003L).status)
            .isEqualTo(OrderPassStatus.SOLD_OUT)
    }

    @Test
    fun `동기화가 끊기면 통행증을 내주지 않는다`() {
        // 발급 가능 수량이 설정된 적이 없는 상태다.
        assertThat(orderPassService.requestPass(PRODUCT_ID, 1101L).status)
            .isEqualTo(OrderPassStatus.SOLD_OUT)
    }
}
