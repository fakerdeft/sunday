package com.sunday.order.adapter.inbound

import com.sunday.order.IntegrationTestSupport
import com.sunday.order.adapter.inbound.dto.CreateOrderRequest
import com.sunday.order.domain.Product
import com.sunday.order.port.outbound.OrderRepository
import com.sunday.order.port.outbound.ProductRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ContextConfiguration(initializers = [IntegrationTestSupport::class])
class OrderDistributedLockTest : DescribeSpec() {

    @Autowired
    private lateinit var orderController: OrderController

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    init {
        extension(SpringExtension())

        beforeEach {
            orderRepository.deleteAll()
        }

        describe("분산 락 동시성 테스트") {
            context("동일 유저가 같은 상품을 동시에 주문하면") {
                it("1번만 성공하고 나머지는 중복 주문 예외가 발생한다") {
                    // given
                    val product = productRepository.save(
                        Product(
                            id = 0L,
                            name = "동시성 테스트 상품",
                            price = BigDecimal("10000"),
                            stock = 100,
                            totalQuantity = 100,
                            isHotDeal = true,
                            hotDealStartTime = LocalDateTime.now().minusHours(1),
                            hotDealEndTime = LocalDateTime.now().plusHours(1)
                        )
                    )

                    val threadCount = 10
                    val executorService = Executors.newFixedThreadPool(32)
                    val latch = CountDownLatch(threadCount)
                    val successCount = AtomicInteger(0)
                    val failCount = AtomicInteger(0)

                    val userId = "1"  // 동일 유저
                    val request = CreateOrderRequest(productId = product.id, quantity = 1)

                    // when
                    for (i in 0 until threadCount) {
                        executorService.submit {
                            try {
                                orderController.createOrderWithDistributedLock(userId, request)
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                failCount.incrementAndGet()
                            } finally {
                                latch.countDown()
                            }
                        }
                    }

                    latch.await()
                    executorService.shutdown()

                    // then
                    successCount.get() shouldBe 1
                    failCount.get() shouldBe threadCount - 1
                }
            }

            context("서로 다른 유저가 같은 상품을 동시에 주문하면") {
                it("재고만큼 성공하고 나머지는 재고 부족 예외가 발생한다") {
                    // given
                    val stockQuantity = 10
                    val product = productRepository.save(
                        Product(
                            id = 0L,
                            name = "재고 정합성 테스트 상품",
                            price = BigDecimal("10000"),
                            stock = stockQuantity,
                            totalQuantity = stockQuantity,
                            isHotDeal = true,
                            hotDealStartTime = LocalDateTime.now().minusHours(1),
                            hotDealEndTime = LocalDateTime.now().plusHours(1)
                        )
                    )

                    val threadCount = 100  // 재고보다 많은 요청
                    val executorService = Executors.newFixedThreadPool(32)
                    val latch = CountDownLatch(threadCount)
                    val successCount = AtomicInteger(0)
                    val failCount = AtomicInteger(0)

                    val request = CreateOrderRequest(productId = product.id, quantity = 1)

                    // when
                    for (i in 1..threadCount) {
                        val userId = i.toString()  // 서로 다른 유저
                        executorService.submit {
                            try {
                                orderController.createOrderWithDistributedLock(userId, request)
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                failCount.incrementAndGet()
                            } finally {
                                latch.countDown()
                            }
                        }
                    }

                    latch.await()
                    executorService.shutdown()

                    // then
                    successCount.get() shouldBe stockQuantity
                    failCount.get() shouldBe threadCount - stockQuantity

                    // DB 재고 검증
                    val updatedProduct = productRepository.findById(product.id)!!
                    updatedProduct.stock shouldBe 0
                }
            }
        }
    }
}
