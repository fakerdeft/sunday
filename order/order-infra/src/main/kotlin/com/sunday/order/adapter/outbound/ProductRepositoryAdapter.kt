package com.sunday.order.adapter.outbound

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sunday.order.domain.Product
import com.sunday.order.port.outbound.ProductRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductRepositoryAdapter(
    private val jpaRepository: ProductJpaRepository,
    private val queryFactory: JPAQueryFactory
) : ProductRepository {

    private val product = QProductJpaEntity.productJpaEntity

    override fun findById(id: Long): Product? {
        return jpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findAll(): List<Product> {
        return jpaRepository.findAll().map { it.toDomain() }
    }

    override fun findHotDeals(): List<Product> {
        return queryFactory
            .selectFrom(product)
            .where(product.isHotDeal.isTrue)
            .fetch()
            .map { it.toDomain() }
    }

    override fun save(product: Product): Product {
        val entity = ProductJpaEntity.fromDomain(product)

        return jpaRepository.save(entity).toDomain()
    }
}
