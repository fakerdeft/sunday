package com.sunday.order.domain

import com.sunday.order.exception.InvalidProductNameException
import com.sunday.order.exception.InvalidProductPriceException
import com.sunday.order.exception.InvalidProductStockException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDateTime

class ProductTest : FunSpec({

    test("Product 정상 생성") {
        val product = Product(
            id = 1L,
            name = "테스트 상품",
            price = BigDecimal("10000"),
            stock = 100,
            totalQuantity = 100
        )

        product.name shouldBe "테스트 상품"
        product.price shouldBe BigDecimal("10000")
        product.stock shouldBe 100
    }

    test("이름이 빈 문자열이면 예외 발생") {
        shouldThrow<InvalidProductNameException> {
            Product(
                id = 1L,
                name = "",
                price = BigDecimal("10000"),
                stock = 100,
                totalQuantity = 100
            )
        }
    }

    test("이름이 공백만 있으면 예외 발생") {
        shouldThrow<InvalidProductNameException> {
            Product(
                id = 1L,
                name = "   ",
                price = BigDecimal("10000"),
                stock = 100,
                totalQuantity = 100
            )
        }
    }

    test("가격이 0 이하면 예외 발생") {
        shouldThrow<InvalidProductPriceException> {
            Product(
                id = 1L,
                name = "테스트 상품",
                price = BigDecimal.ZERO,
                stock = 100,
                totalQuantity = 100
            )
        }

        shouldThrow<InvalidProductPriceException> {
            Product(
                id = 1L,
                name = "테스트 상품",
                price = BigDecimal("-1000"),
                stock = 100,
                totalQuantity = 100
            )
        }
    }

    test("재고가 음수면 예외 발생") {
        shouldThrow<InvalidProductStockException> {
            Product(
                id = 1L,
                name = "테스트 상품",
                price = BigDecimal("10000"),
                stock = -1,
                totalQuantity = 100
            )
        }
    }

    test("totalQuantity가 stock보다 작으면 예외 발생") {
        shouldThrow<IllegalArgumentException> {
            Product(
                id = 1L,
                name = "테스트 상품",
                price = BigDecimal("10000"),
                stock = 100,
                totalQuantity = 50
            )
        }
    }

    test("createHotDeal로 핫딜 상품 생성") {
        val now = LocalDateTime.now()
        val product = Product.createHotDeal(
            name = "핫딜 상품",
            price = BigDecimal("5000"),
            stock = 50,
            startTime = now.minusHours(1),
            endTime = now.plusHours(1)
        )

        product.id shouldBe 0L
        product.isHotDeal shouldBe true
        product.hotDealStartTime shouldBe now.minusHours(1)
        product.hotDealEndTime shouldBe now.plusHours(1)
    }

    test("isHotDealActive - 핫딜이 아니면 false") {
        val product = Product(
            id = 1L,
            name = "일반 상품",
            price = BigDecimal("10000"),
            stock = 100,
            totalQuantity = 100,
            isHotDeal = false
        )

        product.isHotDealActive() shouldBe false
    }

    test("isHotDealActive - 핫딜 기간 내면 true") {
        val now = LocalDateTime.now()
        val product = Product(
            id = 1L,
            name = "핫딜 상품",
            price = BigDecimal("5000"),
            stock = 50,
            totalQuantity = 50,
            isHotDeal = true,
            hotDealStartTime = now.minusHours(1),
            hotDealEndTime = now.plusHours(1)
        )

        product.isHotDealActive() shouldBe true
    }

    test("isHotDealActive - 핫딜 시작 전이면 false") {
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

        product.isHotDealActive() shouldBe false
    }

    test("isHotDealActive - 핫딜 종료 후면 false") {
        val now = LocalDateTime.now()
        val product = Product(
            id = 1L,
            name = "핫딜 상품",
            price = BigDecimal("5000"),
            stock = 50,
            totalQuantity = 50,
            isHotDeal = true,
            hotDealStartTime = now.minusHours(2),
            hotDealEndTime = now.minusHours(1)
        )

        product.isHotDealActive() shouldBe false
    }
})
