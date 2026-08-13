package com.sunday.order.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.ReservationStatus
import jakarta.persistence.LockModeType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class OrderReservationRepository(
    private val jpaRepository: OrderReservationJpaRepository,
    private val queryDsl: JPAQueryFactory
) {
    private val r = QOrderReservationJpaEntity.orderReservationJpaEntity

    fun findById(id: Long): OrderReservation? =
        jpaRepository.findByIdOrNull(id)?.toDomain()

    fun findByIdForUpdate(id: Long): OrderReservation? =
        queryDsl.selectFrom(r)
            .where(r.id.eq(id))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne()
            ?.toDomain()

    fun findByMemberId(memberId: Long): List<OrderReservation> =
        jpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId).map { it.toDomain() }

    fun existsPendingReservation(memberId: Long, productId: Long): Boolean =
        jpaRepository.existsByMemberIdAndProductIdAndStatus(
            memberId,
            productId,
            ReservationStatus.PENDING
        )

    fun findExpiredPendingReservationsForUpdate(batchSize: Int): List<OrderReservation> =
        queryDsl.selectFrom(r)
            .where(
                r.status.eq(ReservationStatus.PENDING),
                r.expireAt.loe(LocalDateTime.now())
            )
            .orderBy(r.expireAt.asc(), r.id.asc())
            .limit(batchSize.toLong())
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetch()
            .map { it.toDomain() }

    fun countByProductIdAndStatus(productId: Long, status: ReservationStatus): Long =
        jpaRepository.countByProductIdAndStatus(productId, status)

    fun save(domain: OrderReservation): OrderReservation =
        jpaRepository.save(OrderReservationJpaEntity.from(domain)).toDomain()

    fun saveAndFlush(domain: OrderReservation): OrderReservation =
        jpaRepository.saveAndFlush(OrderReservationJpaEntity.from(domain)).toDomain()

    fun deleteAll() = jpaRepository.deleteAll()
    fun deleteByProductId(productId: Long) = jpaRepository.deleteByProductId(productId)
}
