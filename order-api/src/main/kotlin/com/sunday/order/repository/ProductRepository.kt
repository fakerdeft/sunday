package com.sunday.order.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sunday.order.domain.Product
import jakarta.persistence.LockModeType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ProductRepository(
    private val jpaRepository: ProductJpaRepository,
    private val queryDsl: JPAQueryFactory
) {
    private val p = QProductJpaEntity.productJpaEntity

    fun findById(id: Long): Product? =
        jpaRepository.findByIdOrNull(id)?.toDomain()

    fun findByIdWithPessimisticLock(id: Long): Product? =
        queryDsl.selectFrom(p)
            .where(p.id.eq(id))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne()
            ?.toDomain()

    fun findAll(): List<Product> =
        jpaRepository.findAll().map { it.toDomain() }

    fun findHotDeals(): List<Product> =
        jpaRepository.findByIsHotDealTrue().map { it.toDomain() }

    fun save(domain: Product): Product =
        jpaRepository.save(ProductJpaEntity.from(domain)).toDomain()

    fun saveAll(products: List<Product>): List<Product> =
        jpaRepository.saveAll(products.map { ProductJpaEntity.from(it) }).map { it.toDomain() }
}
