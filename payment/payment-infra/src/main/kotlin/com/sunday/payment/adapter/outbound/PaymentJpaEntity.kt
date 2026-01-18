package com.sunday.payment.adapter.outbound

import com.sunday.payment.domain.Payment
import com.sunday.payment.domain.PaymentStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment",
    schema = "sunday",
    indexes = [
        Index(name = "idx_payment_order_id", columnList = "order_id"),
        Index(name = "idx_payment_idempotency_key", columnList = "idempotency_key", unique = true)
    ]
)
class PaymentJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PaymentStatus,

    @Column(name = "idempotency_key", nullable = false, unique = true)
    val idempotencyKey: String,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun toDomain(): Payment {
        return Payment(
            id = id,
            orderId = orderId,
            memberId = memberId,
            amount = amount,
            status = status,
            idempotencyKey = idempotencyKey,
            failureReason = failureReason,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun updateFrom(payment: Payment) {
        status = payment.status
        failureReason = payment.failureReason
        updatedAt = payment.updatedAt
    }

    companion object {
        fun fromDomain(payment: Payment): PaymentJpaEntity {
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
}
