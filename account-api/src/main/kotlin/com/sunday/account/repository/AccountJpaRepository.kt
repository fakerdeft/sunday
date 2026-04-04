package com.sunday.account.repository

import org.springframework.data.jpa.repository.JpaRepository

interface AccountJpaRepository : JpaRepository<AccountJpaEntity, Long> {
    fun findByMemberId(memberId: Long): AccountJpaEntity?
    fun findByUserId(userId: String): AccountJpaEntity?
    fun existsByMemberId(memberId: Long): Boolean
    fun existsByUserId(userId: String): Boolean
}
