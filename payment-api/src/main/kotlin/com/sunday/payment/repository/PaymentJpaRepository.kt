package com.sunday.payment.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, Long> {
    fun findByOrderId(orderId: Long): PaymentJpaEntity?
    fun findByIdempotencyKey(idempotencyKey: String): PaymentJpaEntity?
    fun findByMemberIdOrderByCreatedAtDesc(memberId: Long): List<PaymentJpaEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): PaymentJpaEntity?
}
