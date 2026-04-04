package com.sunday.account.repository

import com.sunday.account.domain.Account
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class AccountRepository(
    private val accountJpaRepository: AccountJpaRepository,
    private val accountMapper: AccountMapper
) {

    fun findById(id: Long): Account? {
        return accountJpaRepository.findByIdOrNull(id)?.let { accountMapper.toDomain(it) }
    }

    fun findByMemberId(memberId: Long): Account? {
        return accountJpaRepository.findByMemberId(memberId)?.let { accountMapper.toDomain(it) }
    }

    fun findByUserId(userId: String): Account? {
        return accountJpaRepository.findByUserId(userId)?.let { accountMapper.toDomain(it) }
    }

    fun save(account: Account): Account {
        val entity = if (account.id == 0L) {
            accountMapper.toEntity(account)
        } else {
            val existingEntity = accountJpaRepository.findByIdOrNull(account.id)
                ?: throw IllegalStateException("Account not found for update: ${account.id}")
            existingEntity.updateFrom(account)
            existingEntity
        }
        return accountMapper.toDomain(accountJpaRepository.save(entity))
    }

    fun existsByMemberId(memberId: Long): Boolean {
        return accountJpaRepository.existsByMemberId(memberId)
    }

    fun existsByUserId(userId: String): Boolean {
        return accountJpaRepository.existsByUserId(userId)
    }
}
