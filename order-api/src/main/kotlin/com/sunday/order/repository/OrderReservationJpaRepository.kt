package com.sunday.order.repository

import com.sunday.order.domain.ReservationStatus
import org.springframework.data.jpa.repository.JpaRepository

interface OrderReservationJpaRepository : JpaRepository<OrderReservationJpaEntity, Long> {
    fun findByMemberIdOrderByCreatedAtDesc(memberId: Long): List<OrderReservationJpaEntity>
    fun findByStatus(status: ReservationStatus): List<OrderReservationJpaEntity>
}
