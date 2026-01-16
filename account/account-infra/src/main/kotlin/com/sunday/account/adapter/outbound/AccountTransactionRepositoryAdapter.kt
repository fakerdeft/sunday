package com.sunday.account.adapter.outbound

import com.sunday.account.domain.AccountTransaction
import com.sunday.account.port.outbound.AccountTransactionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

/**
 * AccountTransactionRepository 구현체 (Output Adapter)
 */
@Component
class AccountTransactionRepositoryAdapter(
    private val jpaRepository: AccountTransactionJpaRepository
) : AccountTransactionRepository {

    override fun save(transaction: AccountTransaction): AccountTransaction {
        val entity = AccountTransactionJpaEntity.fromDomain(transaction)
        return jpaRepository.save(entity).toDomain()
    }

    override fun findByAccountId(accountId: Long): List<AccountTransaction> {
        return jpaRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
            .map { it.toDomain() }
    }

    override fun findByAccountId(accountId: Long, page: Int, size: Int): List<AccountTransaction> {
        val pageable = PageRequest.of(page, size)
        return jpaRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
            .map { it.toDomain() }
    }
}
