package com.sunday.order.application

import com.sunday.order.port.inbound.TestUseCase
import com.sunday.order.port.outbound.OrderRepository
import com.sunday.order.port.outbound.ProductRepository
import com.sunday.order.port.outbound.StockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TestService(
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val stockRepository: StockRepository
) : TestUseCase {

    @Transactional
    override fun resetAllData() {
        // 1. 모든 주문 내역 삭제
        orderRepository.deleteAll()

        // 2. 모든 상품 조회 및 재고 리셋
        val products = productRepository.findAll()
        products.forEach { product ->
            val stockQuantity = product.totalQuantity
            product.stock = stockQuantity

            if (product.isHotDeal) {
                // 핫딜 상품: Redis Hash 리셋 + 구매자 목록 초기화
                stockRepository.initializeHotDeal(
                    productId = product.id,
                    stock = stockQuantity,
                    price = product.price.toString(),
                    name = product.name
                )
                stockRepository.clearPurchasedUsers(product.id)
            } else {
                // 일반 상품: stock만 리셋
                stockRepository.initializeStock(product.id, stockQuantity)
            }
        }

        productRepository.saveAll(products)
    }
}
