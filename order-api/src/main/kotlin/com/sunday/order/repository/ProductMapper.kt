package com.sunday.order.repository

import com.sunday.order.domain.Product
import org.springframework.stereotype.Component

@Component
class ProductMapper {

    fun toDomain(entity: ProductJpaEntity): Product {
        return Product(
            id = entity.id,
            name = entity.name,
            price = entity.price,
            stock = entity.stock,
            totalQuantity = entity.totalQuantity,
            isHotDeal = entity.isHotDeal,
            hotDealStartTime = entity.hotDealStartTime,
            hotDealEndTime = entity.hotDealEndTime,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun toEntity(domain: Product): ProductJpaEntity {
        return ProductJpaEntity(
            id = domain.id,
            name = domain.name,
            price = domain.price,
            stock = domain.stock,
            totalQuantity = domain.totalQuantity,
            isHotDeal = domain.isHotDeal,
            hotDealStartTime = domain.hotDealStartTime,
            hotDealEndTime = domain.hotDealEndTime,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
