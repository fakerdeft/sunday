package com.sunday.order.domain

import com.sunday.order.exception.InvalidProductNameException
import com.sunday.order.exception.InvalidProductPriceException
import com.sunday.order.exception.InvalidProductStockException
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 상품 도메인 모델
 */
data class Product(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val stock: Int,
    val totalQuantity: Int, // 총 발행 수량 (최대 재고 기준)
    val isHotDeal: Boolean = false,
    val hotDealStartTime: LocalDateTime? = null,
    val hotDealEndTime: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        if (name.isBlank()) {
            throw InvalidProductNameException()
        }

        if (price <= BigDecimal.ZERO) {
            throw InvalidProductPriceException(price)
        }

        if (stock < 0) {
            throw InvalidProductStockException(stock)
        }

        if (totalQuantity < stock) {
            throw IllegalArgumentException("Total quantity cannot be less than current stock")
        }
    }

    companion object {
        fun createHotDeal(
            name: String,
            price: BigDecimal,
            stock: Int,
            startTime: LocalDateTime,
            endTime: LocalDateTime
        ): Product {
            return Product(
                id = 0L,
                name = name,
                price = price,
                stock = stock,
                totalQuantity = stock, // 초기 생성 시 총 수량 = 재고
                isHotDeal = true,
                hotDealStartTime = startTime,
                hotDealEndTime = endTime
            )
        }
    }

    fun isHotDealActive(): Boolean {
        if (!isHotDeal) return false

        val now = LocalDateTime.now()

        return hotDealStartTime != null &&
               hotDealEndTime != null &&
               now.isAfter(hotDealStartTime) &&
               now.isBefore(hotDealEndTime)
    }
}
