package com.sunday.payment.adapter.outbound

import com.sunday.payment.domain.Payment
import com.sunday.payment.port.outbound.PaymentRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryAdapter(
    private val jpaRepository: PaymentJpaRepository
) : PaymentRepository {

    override fun findById(id: Long): Payment? {
        return jpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findByOrderId(orderId: Long): Payment? {
        return jpaRepository.findByOrderId(orderId)?.toDomain()
    }

    override fun findByIdempotencyKey(idempotencyKey: String): Payment? {
        return jpaRepository.findByIdempotencyKey(idempotencyKey)?.toDomain()
    }

    override fun findByMemberId(memberId: Long): List<Payment> {
        return jpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
            .map { it.toDomain() }
    }

    override fun save(payment: Payment): Payment {
        val entity = if (payment.id == 0L) {
            PaymentJpaEntity.fromDomain(payment)
        } else {
            jpaRepository.findByIdOrNull(payment.id)?.apply {
                updateFrom(payment)
            } ?: PaymentJpaEntity.fromDomain(payment)
        }

        return jpaRepository.save(entity).toDomain()
    }
}
