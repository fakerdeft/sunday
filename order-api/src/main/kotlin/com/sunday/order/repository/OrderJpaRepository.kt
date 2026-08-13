package com.sunday.order.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {
    fun findByMemberIdOrderByCreatedAtDesc(memberId: Long): List<OrderJpaEntity>
    fun existsByMemberIdAndProductId(memberId: Long, productId: Long): Boolean
    fun deleteByProductId(productId: Long)

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderJpaEntity o WHERE o.reservationId = :reservationId")
    fun findByReservationIdForUpdate(@Param("reservationId") reservationId: Long): OrderJpaEntity?
}
