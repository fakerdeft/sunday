package com.sunday.order.repository

import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {
    fun findByMemberIdOrderByCreatedAtDesc(memberId: Long): List<OrderJpaEntity>
    fun existsByMemberIdAndProductId(memberId: Long, productId: Long): Boolean
    fun deleteByProductId(productId: Long)
}
