package com.sunday.account.repository

import com.sunday.account.domain.Transfer
import org.springframework.stereotype.Component

@Component
class TransferMapper {

    fun toDomain(entity: TransferJpaEntity): Transfer {
        return Transfer(
            id = entity.id,
            senderAccountId = entity.senderAccountId,
            senderMemberId = entity.senderMemberId,
            receiverAccountId = entity.receiverAccountId,
            receiverMemberId = entity.receiverMemberId,
            amount = entity.amount,
            status = entity.status,
            idempotencyKey = entity.idempotencyKey,
            description = entity.description,
            failureReason = entity.failureReason,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun toEntity(domain: Transfer): TransferJpaEntity {
        return TransferJpaEntity(
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
}
