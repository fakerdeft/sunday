package com.sunday.account.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sunday.account.domain.AccountTransaction
import com.sunday.account.repository.QAccountTransactionJpaEntity.accountTransactionJpaEntity
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class AccountTransactionRepository(
    private val jpaRepository: AccountTransactionJpaRepository,
    private val accountJpaRepository: AccountJpaRepository,
    private val queryFactory: JPAQueryFactory
) {
    fun save(domain: AccountTransaction): AccountTransaction {
        val accountRef = accountJpaRepository.getReferenceById(domain.accountId)
        return jpaRepository.save(AccountTransactionJpaEntity.from(domain, accountRef)).toDomain()
    }

    fun findByAccountId(accountId: Long): List<AccountTransaction> =
        jpaRepository.findByAccount_IdOrderByCreatedAtDesc(accountId).map { it.toDomain() }

    fun findByAccountId(accountId: Long, page: Int, size: Int): List<AccountTransaction> =
        jpaRepository.findByAccount_IdOrderByCreatedAtDesc(accountId, PageRequest.of(page, size))
            .map { it.toDomain() }

    fun findByAccountIdWithAccount(accountId: Long): List<AccountTransaction> =
        queryFactory.selectFrom(accountTransactionJpaEntity)
            .join(accountTransactionJpaEntity.account).fetchJoin()
            .where(accountTransactionJpaEntity.account.id.eq(accountId))
            .orderBy(accountTransactionJpaEntity.createdAt.desc())
            .fetch()
            .map { it.toDomain() }
}
