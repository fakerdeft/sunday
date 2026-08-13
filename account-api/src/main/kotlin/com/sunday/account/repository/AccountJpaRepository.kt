package com.sunday.account.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AccountJpaRepository : JpaRepository<AccountJpaEntity, Long> {
    fun findByMemberId(memberId: Long): AccountJpaEntity?
    fun findByUserId(userId: String): AccountJpaEntity?
    fun existsByMemberId(memberId: Long): Boolean
    fun existsByUserId(userId: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.memberId = :memberId")
    fun findByMemberIdForUpdate(@Param("memberId") memberId: Long): AccountJpaEntity?
}
