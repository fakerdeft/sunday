package com.sunday

import com.sunday.order.port.outbound.ProductRepository
import com.sunday.order.port.outbound.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 애플리케이션 시작 시 Redis에 상품 재고 초기화
 *
 * DB의 상품 재고를 Redis에 동기화하여
 * 원자적 재고 차감이 가능하도록 함
 */
@Component
class StockInitializer(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun initializeStock() {
        log.info("Initializing stock in Redis...")

        val products = productRepository.findAll()
        var count = 0

        products.forEach { product ->
            stockRepository.initializeStock(product.id, product.stock)
            count++
            log.debug("Initialized stock for product ${product.id}: ${product.stock}")
        }

        log.info("Stock initialization completed. {} products initialized.", count)
    }
}
