package com.sunday.account.repository

import com.sunday.account.domain.AccountTransaction
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class AccountTransactionRepository(
    private val jpaRepository: AccountTransactionJpaRepository,
    private val accountJpaRepository: AccountJpaRepository
) {
    fun save(domain: AccountTransaction): AccountTransaction {
        val accountRef = accountJpaRepository.getReferenceById(domain.accountId)

        return jpaRepository.save(AccountTransactionJpaEntity.from(domain, accountRef)).toDomain()
    }

    fun findByOperationId(operationId: String): AccountTransaction? =
        jpaRepository.findByOperationId(operationId)?.toDomain()

    fun findByAccountId(accountId: Long): List<AccountTransaction> =
        jpaRepository.findByAccountIdOrderByCreatedAtDesc(accountId).map { it.toDomain() }

    fun findByAccountId(accountId: Long, page: Int, size: Int): List<AccountTransaction> =
        jpaRepository.findByAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(page, size))
            .map { it.toDomain() }
}
