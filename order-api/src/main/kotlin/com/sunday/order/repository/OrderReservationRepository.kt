package com.sunday.order.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.ReservationStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class OrderReservationRepository(
    private val jpaRepository: OrderReservationJpaRepository,
    private val queryFactory: JPAQueryFactory
) {
    private val r = QOrderReservationJpaEntity.orderReservationJpaEntity

    fun findById(id: Long): OrderReservation? =
        jpaRepository.findByIdOrNull(id)?.toDomain()

    fun findByIdForUpdate(id: Long): OrderReservation? =
        jpaRepository.findByIdForUpdate(id)?.toDomain()

    fun findByMemberId(memberId: Long): List<OrderReservation> =
        jpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId).map { it.toDomain() }

    fun existsPendingReservation(memberId: Long, productId: Long): Boolean {
        val count = queryFactory
            .select(r.count())
            .from(r)
            .where(
                r.memberId.eq(memberId),
                r.productId.eq(productId),
                r.status.eq(ReservationStatus.PENDING)
            )
            .fetchOne() ?: 0L

        return count > 0
    }

    fun findExpiredPendingReservationsForUpdate(batchSize: Int): List<OrderReservation> =
        jpaRepository.findExpiredPendingForUpdate(LocalDateTime.now(), batchSize).map { it.toDomain() }

    fun countByProductIdAndStatus(productId: Long, status: ReservationStatus): Long =
        jpaRepository.countByProductIdAndStatus(productId, status)

    fun save(domain: OrderReservation): OrderReservation =
        jpaRepository.save(OrderReservationJpaEntity.from(domain)).toDomain()

    fun saveAndFlush(domain: OrderReservation): OrderReservation =
        jpaRepository.saveAndFlush(OrderReservationJpaEntity.from(domain)).toDomain()

    fun deleteAll() = jpaRepository.deleteAll()
    fun deleteByProductId(productId: Long) = jpaRepository.deleteByProductId(productId)
}
