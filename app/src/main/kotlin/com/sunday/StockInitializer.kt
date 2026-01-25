package com.sunday

import com.sunday.order.port.outbound.ProductRepository
import com.sunday.order.port.outbound.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 애플리케이션 시작 시 Redis에 상품 재고 초기화
 *
 * - 일반 상품: stock만 Redis에 저장 (initializeStock)
 * - 핫딜 상품: stock, price, name을 Redis Hash에 저장 (initializeHotDeal)
 *   → 비동기 주문 시 DB 조회 없이 Redis만으로 처리 가능
 */
@Component
class StockInitializer(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun initializeStock() {
        log.info("Initializing stock in Redis...")

        val products = productRepository.findAll()
        var regularCount = 0
        var hotDealCount = 0

        products.forEach { product ->
            val stockQuantity = product.totalQuantity
            product.stock = stockQuantity

            if (product.isHotDeal) {
                // 핫딜 상품: Redis Hash에 stock, price, name 저장
                stockRepository.initializeHotDeal(
                    productId = product.id,
                    stock = stockQuantity,
                    price = product.price.toString(),
                    name = product.name
                )
                hotDealCount++
                log.debug("Initialized hot deal product ${product.id}: stock=$stockQuantity, price=${product.price}")
            } else {
                // 일반 상품: stock만 저장
                stockRepository.initializeStock(product.id, stockQuantity)
                regularCount++
                log.debug("Initialized stock for product ${product.id}: $stockQuantity")
            }
        }

        productRepository.saveAll(products)

        log.info("Stock initialization completed. {} regular products, {} hot deal products initialized.",
            regularCount, hotDealCount)
    }
}
