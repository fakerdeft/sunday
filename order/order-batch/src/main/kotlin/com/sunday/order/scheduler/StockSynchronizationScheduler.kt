package com.sunday.order.scheduler

import com.sunday.order.port.outbound.ProductRepository
import com.sunday.order.port.outbound.StockRepository
import org.apache.logging.log4j.LogManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 재고 동기화 스케줄러 (Redis -> DB)
 *
 * - Redis의 실시간 재고 정보를 주기적으로 DB에 반영 (Write-Back)
 * - 시스템 장애 시 데이터 유실 최소화 및 통계 정확성 확보
 */
@Component
class StockSynchronizationScheduler(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository
) {
    private val log = LogManager.getLogger(javaClass)

    /**
     * 1분마다 실행
     * 모든 상품의 Redis 재고를 조회하여 DB 업데이트
     */
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

            if (updateCount > 0) {
                log.info("Synchronized stock for $updateCount products from Redis to DB")
            }
        } catch (e: Exception) {
            log.error("Failed to synchronize stock", e)
        }
    }
}
