package com.sunday.order.application

import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderStatus
import com.sunday.order.domain.Product
import com.sunday.order.exception.DuplicatePendingOrderException
import com.sunday.order.exception.HotDealNotActiveException
import com.sunday.order.exception.OrderNotFoundException
import com.sunday.order.exception.OutOfStockException
import com.sunday.order.exception.ProductNotFoundException
import com.sunday.order.port.outbound.OrderRepository
import com.sunday.order.port.outbound.OrderStreamPublisher
import com.sunday.order.port.outbound.ProductRepository
import com.sunday.order.port.outbound.StockRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.LocalDateTime

class OrderServiceTest : DescribeSpec({

    val productRepository = mockk<ProductRepository>()
    val orderRepository = mockk<OrderRepository>()
    val stockRepository = mockk<StockRepository>()
    val orderStreamPublisher = mockk<OrderStreamPublisher>()
    val orderService = OrderService(productRepository, orderRepository, stockRepository, orderStreamPublisher)

    describe("getProduct") {
        context("존재하는 상품 ID로 조회하면") {
            it("상품 정보를 반환한다") {
                val product = Product(
                    id = 1L,
                    name = "테스트 상품",
                    price = BigDecimal("10000"),
                    stock = 100,
                    totalQuantity = 100
                )
                every { productRepository.findById(1L) } returns product

                val result = orderService.getProduct(1L)

                result.id shouldBe 1L
                result.name shouldBe "테스트 상품"
            }
        }

        context("존재하지 않는 상품 ID로 조회하면") {
            it("ProductNotFoundException이 발생한다") {
                every { productRepository.findById(999L) } returns null

                shouldThrow<ProductNotFoundException> {
                    orderService.getProduct(999L)
                }
            }
        }
    }

    describe("createOrder") {
        context("정상적인 주문 요청이면") {
            it("주문이 생성되고 재고가 차감된다") {
                val product = Product(
                    id = 1L,
                    name = "테스트 상품",
                    price = BigDecimal("10000"),
                    stock = 100,
                    totalQuantity = 100
                )
                every { orderRepository.existsPendingOrder(100L, 1L) } returns false
                every { productRepository.findByIdWithPessimisticLock(1L) } returns product
                every { productRepository.save(any()) } answers { firstArg() }
                every { orderRepository.save(any()) } answers {
                    firstArg<Order>().copy(id = 1L)
                }

                val result = orderService.createOrderWithPessimisticLock(100L, 1L, 2)

                result.memberId shouldBe 100L
                result.productId shouldBe 1L
                result.quantity shouldBe 2
                result.status shouldBe OrderStatus.PENDING
                verify { productRepository.save(any()) }
            }
        }

        context("이미 대기 중인 주문이 있으면") {
            it("DuplicatePendingOrderException이 발생한다") {
                every { orderRepository.existsPendingOrder(100L, 1L) } returns true

                shouldThrow<DuplicatePendingOrderException> {
                    orderService.createOrderWithPessimisticLock(100L, 1L, 2)
                }
            }
        }

        context("재고가 부족하면") {
            it("OutOfStockException이 발생한다") {
                val product = Product(
                    id = 1L,
                    name = "테스트 상품",
                    price = BigDecimal("10000"),
                    stock = 1,
                    totalQuantity = 100
                )
                every { orderRepository.existsPendingOrder(100L, 1L) } returns false
                every { productRepository.findByIdWithPessimisticLock(1L) } returns product

                shouldThrow<OutOfStockException> {
                    orderService.createOrderWithPessimisticLock(100L, 1L, 2)
                }
            }
        }

        context("핫딜 상품인데 핫딜 기간이 아니면") {
            it("HotDealNotActiveException이 발생한다") {
                val now = LocalDateTime.now()
                val product = Product(
                    id = 1L,
                    name = "핫딜 상품",
                    price = BigDecimal("5000"),
                    stock = 50,
                    totalQuantity = 50,
                    isHotDeal = true,
                    hotDealStartTime = now.plusHours(1),
                    hotDealEndTime = now.plusHours(2)
                )
                every { orderRepository.existsPendingOrder(100L, 1L) } returns false
                every { productRepository.findByIdWithPessimisticLock(1L) } returns product

                shouldThrow<HotDealNotActiveException> {
                    orderService.createOrderWithPessimisticLock(100L, 1L, 2)
                }
            }
        }
    }

    describe("getOrder") {
        context("존재하는 주문 ID로 조회하면") {
            it("주문 정보를 반환한다") {
                val order = Order(
                    id = 1L,
                    memberId = 100L,
                    productId = 1L,
                    productName = "테스트 상품",
                    quantity = 2,
                    unitPrice = BigDecimal("10000"),
                    totalAmount = BigDecimal("20000"),
                    status = OrderStatus.PENDING,
                    reservationKey = "res-key-123",
                    expireAt = LocalDateTime.now().plusMinutes(5)
                )
                every { orderRepository.findById(1L) } returns order

                val result = orderService.getOrder(1L)

                result.id shouldBe 1L
                result.memberId shouldBe 100L
            }
        }

        context("존재하지 않는 주문 ID로 조회하면") {
            it("OrderNotFoundException이 발생한다") {
                every { orderRepository.findById(999L) } returns null

                shouldThrow<OrderNotFoundException> {
                    orderService.getOrder(999L)
                }
            }
        }
    }

    describe("cancelOrder") {
        context("주문을 취소하면") {
            it("CANCELLED 상태가 되고 재고가 복구된다") {
                val order = Order(
                    id = 1L,
                    memberId = 100L,
                    productId = 1L,
                    productName = "테스트 상품",
                    quantity = 2,
                    unitPrice = BigDecimal("10000"),
                    totalAmount = BigDecimal("20000"),
                    status = OrderStatus.PENDING,
                    reservationKey = "res-key-123",
                    expireAt = LocalDateTime.now().plusMinutes(5)
                )
                val product = Product(
                    id = 1L,
                    name = "테스트 상품",
                    price = BigDecimal("10000"),
                    stock = 98,
                    totalQuantity = 100
                )
                every { orderRepository.findById(1L) } returns order
                every { productRepository.findById(1L) } returns product
                every { productRepository.save(any()) } answers { firstArg() }
                every { orderRepository.save(any()) } answers { firstArg() }

                val result = orderService.cancelOrder(1L)

                result.status shouldBe OrderStatus.CANCELLED
                verify { productRepository.save(any()) }
            }
        }
    }

    describe("markOrderAsPaid") {
        context("결제 완료 처리하면") {
            it("PAID 상태가 된다") {
                val order = Order(
                    id = 1L,
                    memberId = 100L,
                    productId = 1L,
                    productName = "테스트 상품",
                    quantity = 2,
                    unitPrice = BigDecimal("10000"),
                    totalAmount = BigDecimal("20000"),
                    status = OrderStatus.PENDING,
                    reservationKey = "res-key-123",
                    expireAt = LocalDateTime.now().plusMinutes(5)
                )
                every { orderRepository.findById(1L) } returns order
                every { orderRepository.save(any()) } answers { firstArg() }

                val result = orderService.markOrderAsPaid(1L)

                result.status shouldBe OrderStatus.PAID
            }
        }
    }

    describe("getMyOrders") {
        context("회원의 주문 목록을 조회하면") {
            it("해당 회원의 주문 목록을 반환한다") {
                val orders = listOf(
                    Order(
                        id = 1L,
                        memberId = 100L,
                        productId = 1L,
                        productName = "상품1",
                        quantity = 1,
                        unitPrice = BigDecimal("10000"),
                        totalAmount = BigDecimal("10000"),
                        status = OrderStatus.PAID,
                        reservationKey = "key-1",
                        expireAt = LocalDateTime.now().plusMinutes(5)
                    ),
                    Order(
                        id = 2L,
                        memberId = 100L,
                        productId = 2L,
                        productName = "상품2",
                        quantity = 2,
                        unitPrice = BigDecimal("5000"),
                        totalAmount = BigDecimal("10000"),
                        status = OrderStatus.PENDING,
                        reservationKey = "key-2",
                        expireAt = LocalDateTime.now().plusMinutes(5)
                    )
                )
                every { orderRepository.findByMemberId(100L) } returns orders

                val result = orderService.getMyOrders(100L)

                result.size shouldBe 2
            }
        }
    }
})
