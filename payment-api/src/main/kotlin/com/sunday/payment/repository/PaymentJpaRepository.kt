package com.sunday.payment.repository

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, Long> {
    fun findByOrderId(orderId: Long): PaymentJpaEntity?
    fun findByIdempotencyKey(idempotencyKey: String): PaymentJpaEntity?
    fun findByMemberIdOrderByCreatedAtDesc(memberId: Long): List<PaymentJpaEntity>
}
