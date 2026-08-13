package com.sunday.account.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sunday.account.domain.Account
import jakarta.persistence.LockModeType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class AccountRepository(
    private val jpaRepository: AccountJpaRepository,
    private val queryDsl: JPAQueryFactory
) {
    private val account = QAccountJpaEntity.accountJpaEntity

    fun findById(id: Long): Account? =
        jpaRepository.findByIdOrNull(id)?.toDomain()

    fun findByMemberId(memberId: Long): Account? =
        jpaRepository.findByMemberId(memberId)?.toDomain()

    fun findByMemberIdForUpdate(memberId: Long): Account? =
        queryDsl.selectFrom(account)
            .where(account.memberId.eq(memberId))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne()
            ?.toDomain()

    fun save(domain: Account): Account =
        jpaRepository.save(AccountJpaEntity.from(domain)).toDomain()

    fun existsByMemberId(memberId: Long): Boolean =
        jpaRepository.existsByMemberId(memberId)
}
