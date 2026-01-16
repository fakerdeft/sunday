package com.sunday.account.adapter.outbound

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * AccountTransaction Spring Data JPA Repository
 */
@Repository
interface AccountTransactionJpaRepository : JpaRepository<AccountTransactionJpaEntity, Long> {
    fun findByAccountIdOrderByCreatedAtDesc(accountId: Long): List<AccountTransactionJpaEntity>
    fun findByAccountIdOrderByCreatedAtDesc(accountId: Long, pageable: Pageable): List<AccountTransactionJpaEntity>
}
