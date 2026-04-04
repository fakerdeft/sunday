package com.sunday.account.repository

import com.sunday.account.domain.AccountTransaction
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class AccountTransactionRepository(
    private val accountTransactionJpaRepository: AccountTransactionJpaRepository,
    private val accountTransactionMapper: AccountTransactionMapper
) {

    fun save(transaction: AccountTransaction): AccountTransaction {
        val entity = accountTransactionMapper.toEntity(transaction)
        return accountTransactionMapper.toDomain(accountTransactionJpaRepository.save(entity))
    }

    fun findByAccountId(accountId: Long): List<AccountTransaction> {
        return accountTransactionJpaRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
            .map { accountTransactionMapper.toDomain(it) }
    }

    fun findByAccountId(accountId: Long, page: Int, size: Int): List<AccountTransaction> {
        val pageable = PageRequest.of(page, size)
        return accountTransactionJpaRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
            .map { accountTransactionMapper.toDomain(it) }
    }
}
