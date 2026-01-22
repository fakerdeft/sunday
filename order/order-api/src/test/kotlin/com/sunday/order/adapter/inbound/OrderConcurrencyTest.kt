package com.sunday.order.adapter.inbound

import com.sunday.order.IntegrationTestSupport
import com.sunday.order.adapter.inbound.dto.CreateOrderRequest
import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderStatus
import com.sunday.order.port.inbound.OrderUseCase
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ContextConfiguration(initializers = [IntegrationTestSupport::class])
class OrderConcurrencyTest : DescribeSpec() {

    @Autowired
    private lateinit var orderController: OrderController

    @MockitoBean
    private lateinit var orderUseCase: OrderUseCase

    init {
        extension(SpringExtension())

        describe("동일한 유저가 같은 상품을 동시에 주문 요청하면") {
            it("분산락에 의해 1번만 성공해야 한다") {
                // given
                val threadCount = 10
                val executorService = Executors.newFixedThreadPool(32)
                val latch = CountDownLatch(threadCount)
                val successCount = AtomicInteger(0)
                val failCount = AtomicInteger(0)

                val userId = "1"
                val productId = 100L
                val request = CreateOrderRequest(productId = productId, quantity = 1)

                val mockOrder = Order(
                    id = 1L,
                    memberId = 1L,
                    productId = productId,
                    productName = "Test Product",
                    quantity = 1,
                    unitPrice = BigDecimal("10000"),
                    totalAmount = BigDecimal("10000"),
                    status = OrderStatus.PENDING,
                    reservationKey = "test-key",
                    expireAt = LocalDateTime.now().plusMinutes(5),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
                given(orderUseCase.createOrder(anyLong(), anyLong(), anyInt())).willReturn(mockOrder)

                // when
                for (i in 0 until threadCount) {
                    executorService.submit {
                        try {
                            orderController.createOrder(userId, request)
                            successCount.incrementAndGet()
                        } catch (e: Exception) {
                            failCount.incrementAndGet()
                        } finally {
                            latch.countDown()
                        }
                    }
                }

                latch.await()

                // then
                successCount.get() shouldBe 1
                failCount.get() shouldBe threadCount - 1
            }
        }
    }
}
