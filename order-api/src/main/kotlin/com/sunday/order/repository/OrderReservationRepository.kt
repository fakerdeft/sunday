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

    fun findByMemberId(memberId: Long): List<OrderReservation> =
        jpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId).map { it.toDomain() }

    fun existsPendingReservation(memberId: Long, productId: Long): Boolean {
        val count = queryFactory
            .select(r.count())
            .from(r)
            .where(
                r.memberId.eq(memberId),
                r.productId.eq(productId),
                r.status.eq(ReservationStatus.PENDING),
                r.expireAt.gt(LocalDateTime.now())
            )
            .fetchOne() ?: 0L
        return count > 0
    }

    fun findExpiredPendingReservations(): List<OrderReservation> =
        queryFactory
            .selectFrom(r)
            .where(
                r.status.eq(ReservationStatus.PENDING),
                r.expireAt.lt(LocalDateTime.now())
            )
            .fetch()
            .map { it.toDomain() }

    fun save(domain: OrderReservation): OrderReservation =
        jpaRepository.save(OrderReservationJpaEntity.from(domain)).toDomain()

    fun deleteAll() = jpaRepository.deleteAll()
}
