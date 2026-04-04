package com.sunday.payment.repository

import com.sunday.payment.domain.Payment
import org.springframework.stereotype.Component

@Component
class PaymentMapper {

    fun toDomain(entity: PaymentJpaEntity): Payment {
        return Payment(
            id = entity.id,
            orderId = entity.orderId,
            memberId = entity.memberId,
            amount = entity.amount,
            status = entity.status,
            idempotencyKey = entity.idempotencyKey,
            failureReason = entity.failureReason,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun toEntity(payment: Payment): PaymentJpaEntity {
        return PaymentJpaEntity(
            id = payment.id,
            orderId = payment.orderId,
            memberId = payment.memberId,
            amount = payment.amount,
            status = payment.status,
            idempotencyKey = payment.idempotencyKey,
            failureReason = payment.failureReason,
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt
        )
    }
}
