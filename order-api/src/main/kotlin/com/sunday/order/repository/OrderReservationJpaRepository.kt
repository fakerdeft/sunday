package com.sunday.order.repository

import com.sunday.order.domain.ReservationStatus
import org.springframework.data.jpa.repository.JpaRepository

interface OrderReservationJpaRepository : JpaRepository<OrderReservationJpaEntity, Long> {
    fun findByReservationKey(reservationKey: String): OrderReservationJpaEntity?
    fun findByMemberIdOrderByCreatedAtDesc(memberId: Long): List<OrderReservationJpaEntity>
    fun existsByMemberIdAndProductIdAndStatus(
        memberId: Long,
        productId: Long,
        status: ReservationStatus
    ): Boolean
    fun countByProductIdAndStatus(productId: Long, status: ReservationStatus): Long
    fun deleteByProductId(productId: Long)
}
