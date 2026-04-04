package com.sunday.account.repository

import com.sunday.account.domain.Account
import org.springframework.stereotype.Component

@Component
class AccountMapper {

    fun toDomain(entity: AccountJpaEntity): Account {
        return Account(
            id = entity.id,
            memberId = entity.memberId,
            userId = entity.userId,
            balance = entity.balance,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun toEntity(domain: Account): AccountJpaEntity {
        return AccountJpaEntity(
            id = domain.id,
            memberId = domain.memberId,
            userId = domain.userId,
            balance = domain.balance,
            version = domain.version,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
