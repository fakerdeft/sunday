package com.sunday.account.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface AccountTransactionJpaRepository : JpaRepository<AccountTransactionJpaEntity, Long> {
    fun findByAccountIdOrderByCreatedAtDesc(accountId: Long): List<AccountTransactionJpaEntity>
    fun findByAccountIdOrderByCreatedAtDesc(accountId: Long, pageable: Pageable): List<AccountTransactionJpaEntity>
}
