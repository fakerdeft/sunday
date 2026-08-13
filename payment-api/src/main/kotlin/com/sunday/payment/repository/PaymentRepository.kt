package com.sunday.payment.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sunday.payment.domain.Payment
import jakarta.persistence.LockModeType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class PaymentRepository(
    private val jpaRepository: PaymentJpaRepository,
    private val queryDsl: JPAQueryFactory
) {
    private val payment = QPaymentJpaEntity.paymentJpaEntity

    fun findById(id: Long): Payment? =
        jpaRepository.findByIdOrNull(id)?.toDomain()

    fun findByIdForUpdate(id: Long): Payment? =
        queryDsl.selectFrom(payment)
            .where(payment.id.eq(id))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne()
            ?.toDomain()

    fun findByOrderId(orderId: Long): Payment? =
        jpaRepository.findByOrderId(orderId)?.toDomain()

    fun findByIdempotencyKey(idempotencyKey: String): Payment? =
        jpaRepository.findByIdempotencyKey(idempotencyKey)?.toDomain()

    fun findByMemberId(memberId: Long): List<Payment> =
        jpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId).map { it.toDomain() }

    fun save(domain: Payment): Payment =
        jpaRepository.save(PaymentJpaEntity.from(domain)).toDomain()

    fun saveAndFlush(domain: Payment): Payment =
        jpaRepository.saveAndFlush(PaymentJpaEntity.from(domain)).toDomain()
}
