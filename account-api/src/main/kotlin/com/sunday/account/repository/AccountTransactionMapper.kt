package com.sunday.account.repository

import com.sunday.account.domain.AccountTransaction
import org.springframework.stereotype.Component

@Component
class AccountTransactionMapper {

    fun toDomain(entity: AccountTransactionJpaEntity): AccountTransaction {
        return AccountTransaction(
            id = entity.id,
            accountId = entity.accountId,
            transactionType = entity.transactionType,
            amount = entity.amount,
            balanceAfter = entity.balanceAfter,
            description = entity.description,
            createdAt = entity.createdAt
        )
    }

    fun toEntity(domain: AccountTransaction): AccountTransactionJpaEntity {
        return AccountTransactionJpaEntity(
            id = domain.id,
            accountId = domain.accountId,
            transactionType = domain.transactionType,
            amount = domain.amount,
            balanceAfter = domain.balanceAfter,
            description = domain.description,
            createdAt = domain.createdAt
        )
    }
}
