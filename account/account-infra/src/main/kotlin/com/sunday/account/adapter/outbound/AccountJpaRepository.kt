package com.sunday.account.adapter.outbound

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Account Spring Data JPA Repository
 */
@Repository
interface AccountJpaRepository : JpaRepository<AccountJpaEntity, Long> {
    fun findByMemberId(memberId: Long): AccountJpaEntity?
    fun findByUserId(userId: String): AccountJpaEntity?
    fun existsByMemberId(memberId: Long): Boolean
    fun existsByUserId(userId: String): Boolean

    /**
     * 낙관적 락으로 조회 (명시적)
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.id = :id")
    fun findByIdWithOptimisticLock(id: Long): AccountJpaEntity?
}
