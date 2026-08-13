package com.sunday.account.repository

import com.sunday.account.domain.Account
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class AccountRepository(private val jpaRepository: AccountJpaRepository) {

    fun findById(id: Long): Account? =
        jpaRepository.findByIdOrNull(id)?.toDomain()

    fun findByMemberId(memberId: Long): Account? =
        jpaRepository.findByMemberId(memberId)?.toDomain()

    fun findByMemberIdForUpdate(memberId: Long): Account? =
        jpaRepository.findByMemberIdForUpdate(memberId)?.toDomain()

    fun findByUserId(userId: String): Account? =
        jpaRepository.findByUserId(userId)?.toDomain()

    fun save(domain: Account): Account =
        jpaRepository.save(AccountJpaEntity.from(domain)).toDomain()

    fun existsByMemberId(memberId: Long): Boolean =
        jpaRepository.existsByMemberId(memberId)

    fun existsByUserId(userId: String): Boolean =
        jpaRepository.existsByUserId(userId)
}
