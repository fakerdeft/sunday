package com.sunday.order.config.scheduler

import com.sunday.order.repository.ProductRepository
import com.sunday.order.repository.RedisStockRepository
import org.apache.logging.log4j.LogManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class StockSynchronizationScheduler(
    private val productRepository: ProductRepository,
    private val stockRepository: RedisStockRepository
) {
    private val log = LogManager.getLogger(javaClass)

    @Scheduled(fixedRate = 60_000)
    @Transactional
    fun syncStock() {
        try {
            val products = productRepository.findAll()
            var updateCount = 0

            products.forEach { product ->
                val redisStock = stockRepository.getStock(product.id)
                if (product.stock != redisStock) {
                    product.stock = redisStock
                    productRepository.save(product)
                    updateCount++
                }
            }

            if (updateCount > 0) log.info("Synchronized stock for $updateCount products from Redis to DB")
        } catch (e: Exception) {
            log.error("Failed to synchronize stock", e)
        }
    }
}
