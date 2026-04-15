package com.sunday.order.application

import com.sunday.order.repository.OrderRepository
import com.sunday.order.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TestService(
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository
) {

    @Transactional
    fun resetAllData() {
        orderRepository.deleteAll()

        val products = productRepository.findAll()
        products.forEach { product ->
            product.stock = product.totalQuantity
        }
        productRepository.saveAll(products)
    }
}
