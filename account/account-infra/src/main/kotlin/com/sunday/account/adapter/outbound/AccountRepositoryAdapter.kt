package com.sunday.account.adapter.outbound

import com.sunday.account.domain.Account
import com.sunday.account.port.outbound.AccountRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * AccountRepository 구현체 (Output Adapter)
 *
 * - Core의 Port 인터페이스를 JPA로 구현
 * - Domain <-> JPA Entity 변환 담당
 */
@Component
class AccountRepositoryAdapter(
    private val jpaRepository: AccountJpaRepository
) : AccountRepository {

    override fun findById(id: Long): Account? {
        return jpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findByMemberId(memberId: Long): Account? {
        return jpaRepository.findByMemberId(memberId)?.toDomain()
    }

    override fun findByUserId(userId: String): Account? {
        return jpaRepository.findByUserId(userId)?.toDomain()
    }

    override fun save(account: Account): Account {
        val entity = if (account.id == 0L) {
            // 신규 생성
            AccountJpaEntity.fromDomain(account)
        } else {
            // 업데이트 - 기존 엔티티 조회 후 수정
            val existingEntity = jpaRepository.findByIdOrNull(account.id)
                ?: throw IllegalStateException("Account not found for update: ${account.id}")
            existingEntity.updateFrom(account)
            existingEntity
        }
        return jpaRepository.save(entity).toDomain()
    }

    override fun existsByMemberId(memberId: Long): Boolean {
        return jpaRepository.existsByMemberId(memberId)
    }

    override fun existsByUserId(userId: String): Boolean {
        return jpaRepository.existsByUserId(userId)
    }
}
