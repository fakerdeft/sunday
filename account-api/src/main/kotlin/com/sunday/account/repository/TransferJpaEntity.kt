package com.sunday.account.repository

import com.sunday.account.domain.Transfer
import com.sunday.account.domain.TransferStatus
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
    name = "transfer",
    schema = "account_service",
    indexes = [
        Index(name = "idx_transfer_idempotency_key", columnList = "idempotency_key", unique = true),
        Index(name = "idx_transfer_sender", columnList = "sender_member_id"),
        Index(name = "idx_transfer_receiver", columnList = "receiver_member_id")
    ]
)
class TransferJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "sender_account_id", nullable = false)
    val senderAccountId: Long,

    @Column(name = "sender_member_id", nullable = false)
    val senderMemberId: Long,

    @Column(name = "receiver_account_id", nullable = false)
    val receiverAccountId: Long,

    @Column(name = "receiver_member_id", nullable = false)
    val receiverMemberId: Long,

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: TransferStatus,

    @Column(name = "idempotency_key", nullable = false, unique = true)
    val idempotencyKey: String,

    @Column(name = "description")
    val description: String?,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun from(domain: Transfer): TransferJpaEntity = TransferJpaEntity(
            id = domain.id,
            senderAccountId = domain.senderAccountId,
            senderMemberId = domain.senderMemberId,
            receiverAccountId = domain.receiverAccountId,
            receiverMemberId = domain.receiverMemberId,
            amount = domain.amount,
            status = domain.status,
            idempotencyKey = domain.idempotencyKey,
            description = domain.description,
            failureReason = domain.failureReason,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    fun toDomain(): Transfer = Transfer(
        id = id,
        senderAccountId = senderAccountId,
        senderMemberId = senderMemberId,
        receiverAccountId = receiverAccountId,
        receiverMemberId = receiverMemberId,
        amount = amount,
        status = status,
        idempotencyKey = idempotencyKey,
        description = description,
        failureReason = failureReason,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
