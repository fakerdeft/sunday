package com.sunday.payment.adapter.outbound

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, Long> {
    fun findByOrderId(orderId: Long): PaymentJpaEntity?
    fun findByIdempotencyKey(idempotencyKey: String): PaymentJpaEntity?
    fun findByMemberIdOrderByCreatedAtDesc(memberId: Long): List<PaymentJpaEntity>
}
