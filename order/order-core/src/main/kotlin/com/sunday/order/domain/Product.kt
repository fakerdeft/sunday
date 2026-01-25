package com.sunday.order.domain

import com.sunday.order.exception.InvalidProductNameException
import com.sunday.order.exception.InvalidProductPriceException
import com.sunday.order.exception.InvalidProductStockException
import com.sunday.order.exception.OutOfStockException
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 상품 도메인 모델
 */
data class Product(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    var stock: Int,
    val totalQuantity: Int,
    val isHotDeal: Boolean = false,
    val hotDealStartTime: LocalDateTime? = null,
    val hotDealEndTime: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now()
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
                totalQuantity = stock,
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

    /**
     * 재고 감소 (도메인 로직)
     */
    fun decreaseStock(quantity: Int) {
        if (stock < quantity) {
            throw OutOfStockException(id, quantity, stock)
        }
        stock -= quantity
        updatedAt = LocalDateTime.now()
    }

    /**
     * 재고 증가 (취소/만료 시 복구)
     * - 최대 재고(totalQuantity)를 초과하지 않도록 제한
     */
    fun increaseStock(quantity: Int) {
        val newStock = stock + quantity
        stock = minOf(newStock, totalQuantity)
        updatedAt = LocalDateTime.now()
    }
}
