package com.sunday.payment.repository

import com.sunday.payment.domain.Payment
import com.sunday.payment.domain.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment",
    schema = "sunday",
    indexes = [
        Index(name = "uq_payment_order_id", columnList = "order_id", unique = true),
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
    companion object {
        fun from(domain: Payment): PaymentJpaEntity = PaymentJpaEntity(
            id = domain.id,
            orderId = domain.orderId,
            memberId = domain.memberId,
            amount = domain.amount,
            status = domain.status,
            idempotencyKey = domain.idempotencyKey,
            failureReason = domain.failureReason,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    fun toDomain(): Payment = Payment(
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
