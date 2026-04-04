package com.sunday.account.repository

import com.sunday.account.domain.Transfer
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class TransferRepository(
    private val transferJpaRepository: TransferJpaRepository,
    private val transferMapper: TransferMapper
) {

    fun findById(id: Long): Transfer? {
        return transferJpaRepository.findByIdOrNull(id)?.let { transferMapper.toDomain(it) }
    }

    fun findByIdempotencyKey(idempotencyKey: String): Transfer? {
        return transferJpaRepository.findByIdempotencyKey(idempotencyKey)?.let { transferMapper.toDomain(it) }
    }

    fun findBySenderMemberId(memberId: Long): List<Transfer> {
        return transferJpaRepository.findBySenderMemberIdOrderByCreatedAtDesc(memberId)
            .map { transferMapper.toDomain(it) }
    }

    fun findByReceiverMemberId(memberId: Long): List<Transfer> {
        return transferJpaRepository.findByReceiverMemberIdOrderByCreatedAtDesc(memberId)
            .map { transferMapper.toDomain(it) }
    }

    fun save(transfer: Transfer): Transfer {
        val entity = if (transfer.id == 0L) {
            transferMapper.toEntity(transfer)
        } else {
            transferJpaRepository.findByIdOrNull(transfer.id)?.apply {
                updateFrom(transfer)
            } ?: transferMapper.toEntity(transfer)
        }
        return transferMapper.toDomain(transferJpaRepository.save(entity))
    }
}
