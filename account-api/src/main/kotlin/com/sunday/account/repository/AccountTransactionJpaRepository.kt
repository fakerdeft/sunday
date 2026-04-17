package com.sunday.account.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface AccountTransactionJpaRepository : JpaRepository<AccountTransactionJpaEntity, Long> {
    fun findByAccount_IdOrderByCreatedAtDesc(accountId: Long): List<AccountTransactionJpaEntity>
    fun findByAccount_IdOrderByCreatedAtDesc(accountId: Long, pageable: Pageable): List<AccountTransactionJpaEntity>
}
