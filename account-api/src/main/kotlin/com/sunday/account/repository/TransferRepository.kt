package com.sunday.account.repository

import com.sunday.account.domain.Transfer
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class TransferRepository(private val jpaRepository: TransferJpaRepository) {

    fun findById(id: Long): Transfer? =
        jpaRepository.findByIdOrNull(id)?.toDomain()

    fun findByIdempotencyKey(idempotencyKey: String): Transfer? =
        jpaRepository.findByIdempotencyKey(idempotencyKey)?.toDomain()

    fun findBySenderMemberId(memberId: Long): List<Transfer> =
        jpaRepository.findBySenderMemberIdOrderByCreatedAtDesc(memberId).map { it.toDomain() }

    fun findByReceiverMemberId(memberId: Long): List<Transfer> =
        jpaRepository.findByReceiverMemberIdOrderByCreatedAtDesc(memberId).map { it.toDomain() }

    fun save(domain: Transfer): Transfer =
        jpaRepository.save(TransferJpaEntity.from(domain)).toDomain()
}
