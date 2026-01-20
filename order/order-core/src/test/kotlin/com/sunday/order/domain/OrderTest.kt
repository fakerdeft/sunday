package com.sunday.order.domain

import com.sunday.order.exception.InvalidOrderQuantityException
import com.sunday.order.exception.InvalidOrderStatusException
import com.sunday.order.exception.InvalidProductPriceException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDateTime

class OrderTest : FunSpec({

    fun createTestProduct() = Product(
        id = 1L,
        name = "테스트 상품",
        price = BigDecimal("10000"),
        stock = 100,
        totalQuantity = 100
    )

    test("Order 정상 생성") {
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

        order.memberId shouldBe 100L
        order.quantity shouldBe 2
        order.totalAmount shouldBe BigDecimal("20000")
        order.status shouldBe OrderStatus.PENDING
    }

    test("Order.create로 새 주문 생성") {
        val product = createTestProduct()

        val order = Order.create(
            memberId = 100L,
            product = product,
            quantity = 2,
            reservationKey = "res-key-123"
        )

        order.id shouldBe 0L
        order.productId shouldBe 1L
        order.productName shouldBe "테스트 상품"
        order.quantity shouldBe 2
        order.unitPrice shouldBe BigDecimal("10000")
        order.totalAmount shouldBe BigDecimal("20000")
        order.status shouldBe OrderStatus.PENDING
    }

    test("수량이 0 이하면 예외 발생") {
        shouldThrow<InvalidOrderQuantityException> {
            Order(
                id = 1L,
                memberId = 100L,
                productId = 1L,
                productName = "테스트 상품",
                quantity = 0,
                unitPrice = BigDecimal("10000"),
                totalAmount = BigDecimal("0"),
                status = OrderStatus.PENDING,
                reservationKey = "res-key-123",
                expireAt = LocalDateTime.now().plusMinutes(5)
            )
        }
    }

    test("단가가 0 이하면 예외 발생") {
        shouldThrow<InvalidProductPriceException> {
            Order(
                id = 1L,
                memberId = 100L,
                productId = 1L,
                productName = "테스트 상품",
                quantity = 2,
                unitPrice = BigDecimal.ZERO,
                totalAmount = BigDecimal.ZERO,
                status = OrderStatus.PENDING,
                reservationKey = "res-key-123",
                expireAt = LocalDateTime.now().plusMinutes(5)
            )
        }
    }

    test("isExpired - 만료 시간이 지나면 true") {
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
            expireAt = LocalDateTime.now().minusMinutes(1)
        )

        order.isExpired() shouldBe true
    }

    test("isExpired - 만료 시간 전이면 false") {
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

        order.isExpired() shouldBe false
    }

    test("markAsPaid - PENDING 상태에서 PAID로 변경") {
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

        val paidOrder = order.markAsPaid()

        paidOrder.status shouldBe OrderStatus.PAID
    }

    test("markAsPaid - PENDING이 아닌 상태에서 호출 시 예외 발생") {
        val paidOrder = Order(
            id = 1L,
            memberId = 100L,
            productId = 1L,
            productName = "테스트 상품",
            quantity = 2,
            unitPrice = BigDecimal("10000"),
            totalAmount = BigDecimal("20000"),
            status = OrderStatus.PAID,
            reservationKey = "res-key-123",
            expireAt = LocalDateTime.now().plusMinutes(5)
        )

        shouldThrow<InvalidOrderStatusException> {
            paidOrder.markAsPaid()
        }
    }

    test("markAsCancelled - PENDING 상태에서 CANCELLED로 변경") {
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

        val cancelled = order.markAsCancelled()

        cancelled.status shouldBe OrderStatus.CANCELLED
    }

    test("markAsCancelled - PAID 상태에서도 CANCELLED로 변경 가능 (환불)") {
        val order = Order(
            id = 1L,
            memberId = 100L,
            productId = 1L,
            productName = "테스트 상품",
            quantity = 2,
            unitPrice = BigDecimal("10000"),
            totalAmount = BigDecimal("20000"),
            status = OrderStatus.PAID,
            reservationKey = "res-key-123",
            expireAt = LocalDateTime.now().plusMinutes(5)
        )

        val cancelled = order.markAsCancelled()

        cancelled.status shouldBe OrderStatus.CANCELLED
    }

    test("markAsExpired - PENDING 상태에서 EXPIRED로 변경") {
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
            expireAt = LocalDateTime.now().minusMinutes(1)
        )

        val expired = order.markAsExpired()

        expired.status shouldBe OrderStatus.EXPIRED
    }
})
