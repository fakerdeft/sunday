package com.sunday.order.adapter.outbound

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderStatus
import com.sunday.order.port.outbound.OrderRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository,
    private val queryFactory: JPAQueryFactory
) : OrderRepository {

    private val order = QOrderJpaEntity.orderJpaEntity

    override fun findById(id: Long): Order? {
        return jpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findByMemberId(memberId: Long): List<Order> {
        return jpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
            .map { it.toDomain() }
    }

    override fun findByStatus(status: OrderStatus): List<Order> {
        return jpaRepository.findByStatus(status)
            .map { it.toDomain() }
    }

    override fun findExpiredPendingOrders(): List<Order> {
        return queryFactory
            .selectFrom(order)
            .where(
                order.status.eq(OrderStatus.PENDING),
                order.expireAt.lt(LocalDateTime.now())
            )
            .fetch()
            .map { it.toDomain() }
    }

    override fun existsPendingOrder(memberId: Long, productId: Long): Boolean {
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

    override fun save(order: Order): Order {
        val entity = if (order.id == 0L) {
            OrderJpaEntity.fromDomain(order)
        } else {
            jpaRepository.findByIdOrNull(order.id)?.apply {
                updateFrom(order)
            } ?: OrderJpaEntity.fromDomain(order)
        }

        return jpaRepository.save(entity).toDomain()
    }

    override fun deleteAll() {
        jpaRepository.deleteAll()
    }
}
