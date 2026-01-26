package com.sunday.order.adapter.inbound

import com.sunday.order.IntegrationTestSupport
import com.sunday.order.adapter.inbound.dto.CreateOrderRequest
import com.sunday.order.domain.Product
import com.sunday.order.port.outbound.OrderRepository
import com.sunday.order.port.outbound.ProductRepository
import com.sunday.order.port.outbound.StockRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.awaitility.Awaitility.await
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ContextConfiguration(initializers = [IntegrationTestSupport::class])
class OrderAsyncTest : DescribeSpec() {

    @Autowired
    private lateinit var orderController: OrderController

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var stockRepository: StockRepository

    init {
        extension(SpringExtension())

        describe("비동기 주문 동시성 테스트") {
            context("동일 유저가 같은 상품을 동시에 주문하면") {
                it("1번만 성공하고 나머지는 중복 주문 예외가 발생한다") {
                    // given
                    orderRepository.deleteAll()

                    val product = productRepository.save(
                        Product(
                            id = 0L,
                            name = "비동기 동시성 테스트 상품",
                            price = BigDecimal("10000"),
                            stock = 100,
                            totalQuantity = 100,
                            isHotDeal = true,
                            hotDealStartTime = LocalDateTime.now().minusHours(1),
                            hotDealEndTime = LocalDateTime.now().plusHours(1)
                        )
                    )

                    // Redis에 핫딜 상품 정보 초기화
                    stockRepository.initializeHotDeal(
                        productId = product.id,
                        stock = product.stock,
                        price = product.price.toString(),
                        name = product.name
                    )
                    stockRepository.clearPurchasedUsers(product.id)

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
                                val response = orderController.createOrderAsync(userId, request)
                                response.reservationKey shouldStartWith "async:"
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

                    // Consumer가 DB에 저장할 때까지 대기
                    await()
                        .atMost(5, TimeUnit.SECONDS)
                        .untilAsserted {
                            val orders = orderRepository.findByMemberId(userId.toLong())
                            orders.size shouldBe 1
                        }
                }
            }

            context("서로 다른 유저가 같은 상품을 동시에 주문하면") {
                it("재고만큼 성공하고 나머지는 재고 부족 예외가 발생한다") {
                    // given
                    orderRepository.deleteAll()

                    val stockQuantity = 10
                    val product = productRepository.save(
                        Product(
                            id = 0L,
                            name = "비동기 재고 정합성 테스트 상품",
                            price = BigDecimal("10000"),
                            stock = stockQuantity,
                            totalQuantity = stockQuantity,
                            isHotDeal = true,
                            hotDealStartTime = LocalDateTime.now().minusHours(1),
                            hotDealEndTime = LocalDateTime.now().plusHours(1)
                        )
                    )

                    // Redis에 핫딜 상품 정보 초기화
                    stockRepository.initializeHotDeal(
                        productId = product.id,
                        stock = product.stock,
                        price = product.price.toString(),
                        name = product.name
                    )
                    stockRepository.clearPurchasedUsers(product.id)

                    val threadCount = 100  // 재고보다 많은 요청
                    val executorService = Executors.newFixedThreadPool(32)
                    val latch = CountDownLatch(threadCount)
                    val successCount = AtomicInteger(0)
                    val failCount = AtomicInteger(0)

                    val request = CreateOrderRequest(productId = product.id, quantity = 1)

                    // when
                    for (i in 1..threadCount) {
                        val userId = (i + 1000).toString()  // 서로 다른 유저
                        executorService.submit {
                            try {
                                val response = orderController.createOrderAsync(userId, request)
                                response.reservationKey shouldStartWith "async:"
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

                    // Consumer가 DB에 저장할 때까지 대기
                    await()
                        .atMost(5, TimeUnit.SECONDS)
                        .untilAsserted {
                            val orders = orderRepository.findAll()
                                .filter { it.productId == product.id }
                            orders.size shouldBe stockQuantity
                        }
                }
            }

            context("비동기 주문 생성 시") {
                it("Redis Stream에 메시지를 발행하고 Consumer가 DB에 저장한다") {
                    // given
                    orderRepository.deleteAll()

                    val product = productRepository.save(
                        Product(
                            id = 0L,
                            name = "비동기 저장 테스트 상품",
                            price = BigDecimal("15000"),
                            stock = 50,
                            totalQuantity = 50,
                            isHotDeal = true,
                            hotDealStartTime = LocalDateTime.now().minusHours(1),
                            hotDealEndTime = LocalDateTime.now().plusHours(1)
                        )
                    )

                    // Redis에 핫딜 상품 정보 초기화
                    stockRepository.initializeHotDeal(
                        productId = product.id,
                        stock = product.stock,
                        price = product.price.toString(),
                        name = product.name
                    )
                    stockRepository.clearPurchasedUsers(product.id)

                    val userId = "999"
                    val request = CreateOrderRequest(productId = product.id, quantity = 2)

                    // when
                    val response = orderController.createOrderAsync(userId, request)

                    // then
                    response.status shouldBe "PROCESSING"
                    response.reservationKey shouldStartWith "async:"
                    response.reservationKey shouldNotBe null

                    // Consumer가 DB에 저장할 때까지 대기 후 검증
                    await()
                        .atMost(5, TimeUnit.SECONDS)
                        .untilAsserted {
                            val orders = orderRepository.findByMemberId(userId.toLong())
                            orders.size shouldBe 1

                            val savedOrder = orders.first()
                            savedOrder.memberId shouldBe userId.toLong()
                            savedOrder.productId shouldBe product.id
                            savedOrder.productName shouldBe product.name
                            savedOrder.quantity shouldBe 2
                            savedOrder.unitPrice.compareTo(product.price) shouldBe 0
                            savedOrder.totalAmount.compareTo(BigDecimal("30000")) shouldBe 0
                            savedOrder.reservationKey shouldBe response.reservationKey
                        }
                }
            }

            context("상품이 Redis에 없으면") {
                it("ProductNotFoundException이 발생한다") {
                    // given
                    val nonExistentProductId = 99999L
                    val userId = "1"
                    val request = CreateOrderRequest(productId = nonExistentProductId, quantity = 1)

                    // when & then
                    try {
                        orderController.createOrderAsync(userId, request)
                        throw AssertionError("예외가 발생해야 합니다")
                    } catch (e: Exception) {
                        e.message shouldBe "상품을 찾을 수 없습니다: $nonExistentProductId"
                    }
                }
            }
        }
    }
}
