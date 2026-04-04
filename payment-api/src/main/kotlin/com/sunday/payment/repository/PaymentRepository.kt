package com.sunday.payment.repository

import com.sunday.payment.domain.Payment
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class PaymentRepository(
    private val paymentJpaRepository: PaymentJpaRepository,
    private val paymentMapper: PaymentMapper
) {

    fun findById(id: Long): Payment? {
        return paymentJpaRepository.findByIdOrNull(id)?.let { paymentMapper.toDomain(it) }
    }

    fun findByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findByOrderId(orderId)?.let { paymentMapper.toDomain(it) }
    }

    fun findByIdempotencyKey(idempotencyKey: String): Payment? {
        return paymentJpaRepository.findByIdempotencyKey(idempotencyKey)?.let { paymentMapper.toDomain(it) }
    }

    fun findByMemberId(memberId: Long): List<Payment> {
        return paymentJpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId).map { paymentMapper.toDomain(it) }
    }

    fun save(payment: Payment): Payment {
        val entity = if (payment.id == 0L) {
            paymentMapper.toEntity(payment)
        } else {
            paymentJpaRepository.findByIdOrNull(payment.id)?.apply {
                updateFrom(payment)
            } ?: paymentMapper.toEntity(payment)
        }
        return paymentMapper.toDomain(paymentJpaRepository.save(entity))
    }
}
