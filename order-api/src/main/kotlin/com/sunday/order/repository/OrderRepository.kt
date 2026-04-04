package com.sunday.order.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class OrderRepository(
    private val orderJpaRepository: OrderJpaRepository,
    private val orderMapper: OrderMapper,
    private val queryFactory: JPAQueryFactory
) {
    private val order = QOrderJpaEntity.orderJpaEntity

    fun findById(id: Long): Order? {
        return orderJpaRepository.findByIdOrNull(id)?.let { orderMapper.toDomain(it) }
    }

    fun findByMemberId(memberId: Long): List<Order> {
        return orderJpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
            .map { orderMapper.toDomain(it) }
    }

    fun findExpiredPendingOrders(): List<Order> {
        return queryFactory
            .selectFrom(order)
            .where(
                order.status.eq(OrderStatus.PENDING),
                order.expireAt.lt(LocalDateTime.now())
            )
            .fetch()
            .map { orderMapper.toDomain(it) }
    }

    fun existsPendingOrder(memberId: Long, productId: Long): Boolean {
        val count = queryFactory
            .select(order.count())
            .from(order)
            .where(
                order.memberId.eq(memberId),
                order.productId.eq(productId),
                order.status.eq(OrderStatus.PENDING),
                order.expireAt.gt(LocalDateTime.now())
            )
            .fetchOne() ?: 0L
        return count > 0
    }

    fun save(orderDomain: Order): Order {
        val entity = if (orderDomain.id == 0L) {
            orderMapper.toEntity(orderDomain)
        } else {
            orderJpaRepository.findByIdOrNull(orderDomain.id)?.apply {
                updateFrom(orderDomain)
            } ?: orderMapper.toEntity(orderDomain)
        }
        return orderMapper.toDomain(orderJpaRepository.save(entity))
    }

    fun findAll(): List<Order> {
        return orderJpaRepository.findAll().map { orderMapper.toDomain(it) }
    }

    fun deleteAll() {
        orderJpaRepository.deleteAll()
    }
}
