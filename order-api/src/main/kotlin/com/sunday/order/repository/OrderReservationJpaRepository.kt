package com.sunday.order.repository

import com.sunday.order.domain.ReservationStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OrderReservationJpaRepository : JpaRepository<OrderReservationJpaEntity, Long> {
    fun findByMemberIdOrderByCreatedAtDesc(memberId: Long): List<OrderReservationJpaEntity>
    fun findByStatus(status: ReservationStatus): List<OrderReservationJpaEntity>
    fun countByProductIdAndStatus(productId: Long, status: ReservationStatus): Long
    fun deleteByProductId(productId: Long)

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM OrderReservationJpaEntity r WHERE r.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): OrderReservationJpaEntity?

    @Query(
        value = """
            SELECT *
            FROM sunday.order_reservations
            WHERE status = 'PENDING' AND expire_at <= :now
            ORDER BY expire_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findExpiredPendingForUpdate(
        @Param("now") now: LocalDateTime,
        @Param("batchSize") batchSize: Int
    ): List<OrderReservationJpaEntity>
}
