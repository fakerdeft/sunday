package com.sunday.account.adapter.outbound

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Account Spring Data JPA Repository
 *
 * 복잡한 쿼리는 AccountQueryRepository 사용
 */
@Repository
interface AccountJpaRepository : JpaRepository<AccountJpaEntity, Long> {
    fun findByMemberId(memberId: Long): AccountJpaEntity?
    fun findByUserId(userId: String): AccountJpaEntity?
    fun existsByMemberId(memberId: Long): Boolean
    fun existsByUserId(userId: String): Boolean
}
