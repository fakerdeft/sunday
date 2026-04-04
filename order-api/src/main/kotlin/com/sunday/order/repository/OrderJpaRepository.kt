package com.sunday.order.repository

import com.sunday.order.domain.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {
    fun findByMemberIdOrderByCreatedAtDesc(memberId: Long): List<OrderJpaEntity>
    fun findByStatus(status: OrderStatus): List<OrderJpaEntity>
}
