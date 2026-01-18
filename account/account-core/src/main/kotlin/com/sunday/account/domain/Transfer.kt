package com.sunday.account.domain

import com.sunday.account.exception.TransferNotReversibleException
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 송금 도메인 모델
 *
 * 모놀리식 환경: PENDING → COMPLETED (단일 트랜잭션)
 * MSA 환경: Saga 패턴으로 확장 가능
 */
data class Transfer(
    val id: Long,
    val senderAccountId: Long,
    val senderMemberId: Long,
    val receiverAccountId: Long,
    val receiverMemberId: Long,
    val amount: BigDecimal,
    val status: TransferStatus,
    val idempotencyKey: String,
    val description: String?,
    val failureReason: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(amount > BigDecimal.ZERO) { "Transfer amount must be positive" }
        require(senderAccountId != receiverAccountId) { "Cannot transfer to same account" }
        require(idempotencyKey.isNotBlank()) { "Idempotency key cannot be blank" }
    }

    companion object {
        fun create(
            senderAccountId: Long,
            senderMemberId: Long,
            receiverAccountId: Long,
            receiverMemberId: Long,
            amount: BigDecimal,
            idempotencyKey: String,
            description: String? = null
        ): Transfer {
            return Transfer(
                id = 0L,
                senderAccountId = senderAccountId,
                senderMemberId = senderMemberId,
                receiverAccountId = receiverAccountId,
                receiverMemberId = receiverMemberId,
                amount = amount,
                status = TransferStatus.PENDING,
                idempotencyKey = idempotencyKey,
                description = description ?: "계좌 송금"
            )
        }
    }

    fun complete(): Transfer {
        check(status == TransferStatus.PENDING) { "Can only complete PENDING transfer" }
        return copy(
            status = TransferStatus.COMPLETED,
            updatedAt = LocalDateTime.now()
        )
    }

    fun fail(reason: String): Transfer {
        check(status == TransferStatus.PENDING) { "Can only fail PENDING transfer" }
        return copy(
            status = TransferStatus.FAILED,
            failureReason = reason,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 송금 취소 (환불)
     *
     * @throws TransferNotReversibleException COMPLETED 상태가 아닌 경우
     */
    fun reverse(): Transfer {
        if (status != TransferStatus.COMPLETED) {
            throw TransferNotReversibleException(id, status.name)
        }
        return copy(
            status = TransferStatus.REVERSED,
            updatedAt = LocalDateTime.now()
        )
    }
}
