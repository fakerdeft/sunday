package com.sunday.order.adapter.outbound

import com.sunday.order.domain.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Order Spring Data JPA Repository
 *
 * 복잡한 쿼리는 OrderQueryRepository 사용
 */
@Repository
interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {

    fun findByMemberIdOrderByCreatedAtDesc(memberId: Long): List<OrderJpaEntity>

    fun findByStatus(status: OrderStatus): List<OrderJpaEntity>
}
