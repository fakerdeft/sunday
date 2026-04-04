package com.sunday.order.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sunday.order.domain.Product
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ProductRepository(
    private val productJpaRepository: ProductJpaRepository,
    private val productMapper: ProductMapper,
    private val queryFactory: JPAQueryFactory
) {
    private val product = QProductJpaEntity.productJpaEntity

    fun findById(id: Long): Product? {
        return productJpaRepository.findByIdOrNull(id)?.let { productMapper.toDomain(it) }
    }

    fun findByIdWithPessimisticLock(id: Long): Product? {
        return productJpaRepository.findByIdWithPessimisticLock(id)
            .orElse(null)
            ?.let { productMapper.toDomain(it) }
    }

    fun findAll(): List<Product> {
        return productJpaRepository.findAll().map { productMapper.toDomain(it) }
    }

    fun findHotDeals(): List<Product> {
        return queryFactory
            .selectFrom(product)
            .where(product.isHotDeal.isTrue)
            .fetch()
            .map { productMapper.toDomain(it) }
    }

    fun save(productDomain: Product): Product {
        return productMapper.toDomain(productJpaRepository.save(productMapper.toEntity(productDomain)))
    }

    fun saveAll(products: List<Product>): List<Product> {
        return productJpaRepository.saveAll(products.map { productMapper.toEntity(it) })
            .map { productMapper.toDomain(it) }
    }
}
