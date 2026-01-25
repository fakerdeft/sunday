package com.sunday.order.adapter.inbound

import com.sunday.order.IntegrationTestSupport
import com.sunday.order.domain.Product
import com.sunday.order.port.inbound.OrderUseCase
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
class OrderPessimisticLockTest : DescribeSpec() {

    @Autowired
    private lateinit var orderUseCase: OrderUseCase

    @Autowired
    private lateinit var productRepository: ProductRepository

    init {
        extension(SpringExtension())

        describe("비관적 락 동시성 테스트") {

            // 테스트 데이터 초기화
            val stockQuantity = 100
            val threadCount = 100 // 재고만큼 요청

            // 상품 생성
            val product = Product(
                id = 0L,
                name = "비관적 락 테스트 상품",
                price = BigDecimal("10000"),
                stock = stockQuantity,
                totalQuantity = stockQuantity,
                isHotDeal = true,
                hotDealStartTime = LocalDateTime.now().minusHours(1),
                hotDealEndTime = LocalDateTime.now().plusHours(1)
            )
            val savedProduct = productRepository.save(product)
            val productId = savedProduct.id

            it("동시에 ${threadCount}명이 주문하면 재고가 정확히 차감되어야 한다") {
                // given
                val executorService = Executors.newFixedThreadPool(32)
                val latch = CountDownLatch(threadCount)
                val successCount = AtomicInteger(0)
                val failCount = AtomicInteger(0)

                // when
                for (i in 1..threadCount) {
                    val memberId = i.toLong() // 서로 다른 유저
                    executorService.submit {
                        try {
                            // 비관적 락 메서드 호출
                            orderUseCase.createOrderWithPessimisticLock(
                                memberId = memberId,
                                productId = productId,
                                quantity = 1
                            )
                            successCount.incrementAndGet()
                        } catch (_: Exception) {
                            // e.printStackTrace()
                            failCount.incrementAndGet()
                        } finally {
                            latch.countDown()
                        }
                    }
                }

                latch.await()

                // then
                // 1. 성공 횟수는 요청 수와 같아야 함
                successCount.get() shouldBe threadCount
                failCount.get() shouldBe 0

                // 2. DB 재고는 0이어야 함
                // 트랜잭션이 끝난 후 조회해야 하므로 별도 트랜잭션이나 리포지토리 직접 조회
                val updatedProduct = productRepository.findById(productId)!!
                updatedProduct.stock shouldBe 0
            }
        }
    }
}
