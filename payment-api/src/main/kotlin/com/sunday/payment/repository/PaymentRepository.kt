package com.sunday.payment.repository

import com.sunday.payment.domain.Payment
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class PaymentRepository(private val jpaRepository: PaymentJpaRepository) {

    fun findById(id: Long): Payment? =
        jpaRepository.findByIdOrNull(id)?.toDomain()

    fun findByOrderId(orderId: Long): Payment? =
        jpaRepository.findByOrderId(orderId)?.toDomain()

    fun findByIdempotencyKey(idempotencyKey: String): Payment? =
        jpaRepository.findByIdempotencyKey(idempotencyKey)?.toDomain()

    fun findByMemberId(memberId: Long): List<Payment> =
        jpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId).map { it.toDomain() }

    fun save(domain: Payment): Payment =
        jpaRepository.save(PaymentJpaEntity.from(domain)).toDomain()
}
