package com.sunday.order.port.outbound

import com.sunday.order.domain.Product

/**
 * Product Repository (Output Port)
 */
interface ProductRepository {
    fun findById(id: Long): Product?
    fun findByIdWithPessimisticLock(id: Long): Product?
    fun findAll(): List<Product>
    fun findHotDeals(): List<Product>
    fun save(product: Product): Product
    fun saveAll(products: List<Product>): List<Product>
}
