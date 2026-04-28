package com.sunday.order.application

import com.sunday.order.domain.ProductStock
import com.sunday.order.domain.StockStatus
import com.sunday.order.repository.OrderRepository
import com.sunday.order.repository.OrderReservationRepository
import com.sunday.order.repository.ProductRepository
import com.sunday.order.repository.ProductStockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class TestService(
    private val productRepository: ProductRepository,
    private val reservationRepository: OrderReservationRepository,
    private val orderRepository: OrderRepository,
    private val productStockRepository: ProductStockRepository,
    private val stockCasManager: StockCasManager,
    private val redisTokenQueueManager: RedisTokenQueueManager,
    private val redisStockCounter: RedisStockCounter
) {
    @Transactional
    fun resetAllData() {
        orderRepository.deleteAll()
        reservationRepository.deleteAll()

        val products = productRepository.findAll()
        products.forEach { product ->
            product.stock = product.totalQuantity
        }
        productRepository.saveAll(products)

        products.forEach { p -> resetStockForProduct(p.id, p.totalQuantity) }
    }

    @Transactional
    fun resetProductForScale(productId: Long, quantity: Int) {
        orderRepository.deleteByProductId(productId)
        reservationRepository.deleteByProductId(productId)

        val product = productRepository.findById(productId) ?: return
        productRepository.save(product.copy(
            stock = quantity,
            totalQuantity = quantity,
            hotDealStartTime = LocalDateTime.now().minusMinutes(1),
            hotDealEndTime = LocalDateTime.now().plusHours(24)
        ))

        resetStockForProduct(productId, quantity)
    }

    private fun resetStockForProduct(productId: Long, quantity: Int) {
        productStockRepository.deleteByProductId(productId)
        val stocks = (1..quantity).map {
            ProductStock(id = 0L, productId = productId, status = StockStatus.AVAILABLE,
                version = 0L, reservedBy = null, createdAt = LocalDateTime.now())
        }
        productStockRepository.saveAll(stocks)
        stockCasManager.reset(productId, quantity)
        redisTokenQueueManager.initQueue(productId, quantity)
        redisStockCounter.set(productId, quantity)
    }
}
