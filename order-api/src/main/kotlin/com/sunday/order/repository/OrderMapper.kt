package com.sunday.order.repository

import com.sunday.order.domain.Order
import org.springframework.stereotype.Component

@Component
class OrderMapper {

    fun toDomain(entity: OrderJpaEntity): Order {
        return Order(
            id = entity.id,
            memberId = entity.memberId,
            productId = entity.productId,
            productName = entity.productName,
            quantity = entity.quantity,
            unitPrice = entity.unitPrice,
            totalAmount = entity.totalAmount,
            status = entity.status,
            reservationKey = entity.reservationKey,
            expireAt = entity.expireAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun toEntity(domain: Order): OrderJpaEntity {
        return OrderJpaEntity(
            id = domain.id,
            memberId = domain.memberId,
            productId = domain.productId,
            productName = domain.productName,
            quantity = domain.quantity,
            unitPrice = domain.unitPrice,
            totalAmount = domain.totalAmount,
            status = domain.status,
            reservationKey = domain.reservationKey,
            expireAt = domain.expireAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
