package com.sunday.account.repository

import com.sunday.account.domain.TransferStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "transfer",
    schema = "sunday",
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_account_id", nullable = false, insertable = false, updatable = false)
    val senderAccount: AccountJpaEntity? = null,

    @Column(name = "sender_account_id", nullable = false)
    val senderAccountId: Long,

    @Column(name = "sender_member_id", nullable = false)
    val senderMemberId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_account_id", nullable = false, insertable = false, updatable = false)
    val receiverAccount: AccountJpaEntity? = null,

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
    fun updateFrom(transfer: com.sunday.account.domain.Transfer) {
        this.status = transfer.status
        this.failureReason = transfer.failureReason
        this.updatedAt = transfer.updatedAt
    }
}
