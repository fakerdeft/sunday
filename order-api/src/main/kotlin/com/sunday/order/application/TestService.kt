package com.sunday.order.application

import com.sunday.order.repository.OrderRepository
import com.sunday.order.repository.ProductRepository
import com.sunday.order.repository.RedisStockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TestService(
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val stockRepository: RedisStockRepository
) {

    @Transactional
    fun resetAllData() {
        orderRepository.deleteAll()

        val products = productRepository.findAll()
        products.forEach { product ->
            val stockQuantity = product.totalQuantity
            product.stock = stockQuantity

            if (product.isHotDeal) {
                stockRepository.initializeHotDeal(product.id, stockQuantity, product.price.toString(), product.name)
                stockRepository.clearPurchasedUsers(product.id)
            } else {
                stockRepository.initializeStock(product.id, stockQuantity)
            }
        }

        productRepository.saveAll(products)
    }
}
