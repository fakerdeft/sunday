package com.sunday.order.domain

import java.math.BigDecimal
import java.time.LocalDateTime

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
        if (name.isBlank()) throw InvalidProductNameException()
        if (price <= BigDecimal.ZERO) throw InvalidProductPriceException(price)
        if (stock < 0) throw InvalidProductStockException(stock)
        if (totalQuantity < stock) throw IllegalArgumentException("Total quantity cannot be less than current stock")
    }

    companion object {
        fun createHotDeal(name: String, price: BigDecimal, stock: Int, startTime: LocalDateTime, endTime: LocalDateTime): Product {
            return Product(id = 0L, name = name, price = price, stock = stock, totalQuantity = stock,
                isHotDeal = true, hotDealStartTime = startTime, hotDealEndTime = endTime)
        }
    }

    fun isHotDealActive(): Boolean {
        if (!isHotDeal) return false
        val now = LocalDateTime.now()
        return hotDealStartTime != null && hotDealEndTime != null &&
                now.isAfter(hotDealStartTime) && now.isBefore(hotDealEndTime)
    }

    fun decreaseStock(quantity: Int) {
        if (stock < quantity) throw OutOfStockException(id, quantity, stock)
        stock -= quantity
        updatedAt = LocalDateTime.now()
    }

    fun increaseStock(quantity: Int) {
        stock = minOf(stock + quantity, totalQuantity)
        updatedAt = LocalDateTime.now()
    }
}
