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
    private val redisTokenQueueManager: RedisTokenQueueManager
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

        // product_stock 초기화
        products.forEach { p ->
            productStockRepository.deleteByProductId(p.id)
            val stocks = (1..p.totalQuantity).map {
                ProductStock(id = 0L, productId = p.id, status = StockStatus.AVAILABLE,
                    version = 0L, reservedBy = null, createdAt = LocalDateTime.now())
            }
            productStockRepository.saveAll(stocks)

            // CAS 초기화
            stockCasManager.reset(p.id, p.totalQuantity)
            // Redis Token Queue 초기화
            redisTokenQueueManager.initQueue(p.id, p.totalQuantity)
        }
    }
}
