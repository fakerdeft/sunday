package com.sunday.account.adapter.outbound

import com.sunday.account.domain.Transfer
import com.sunday.account.port.outbound.TransferRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class TransferRepositoryAdapter(
    private val jpaRepository: TransferJpaRepository
) : TransferRepository {

    override fun findById(id: Long): Transfer? {
        return jpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findByIdempotencyKey(idempotencyKey: String): Transfer? {
        return jpaRepository.findByIdempotencyKey(idempotencyKey)?.toDomain()
    }

    override fun findBySenderMemberId(memberId: Long): List<Transfer> {
        return jpaRepository.findBySenderMemberIdOrderByCreatedAtDesc(memberId)
            .map { it.toDomain() }
    }

    override fun findByReceiverMemberId(memberId: Long): List<Transfer> {
        return jpaRepository.findByReceiverMemberIdOrderByCreatedAtDesc(memberId)
            .map { it.toDomain() }
    }

    override fun save(transfer: Transfer): Transfer {
        val entity = if (transfer.id == 0L) {
            TransferJpaEntity.fromDomain(transfer)
        } else {
            jpaRepository.findByIdOrNull(transfer.id)?.apply {
                updateFrom(transfer)
            } ?: TransferJpaEntity.fromDomain(transfer)
        }

        return jpaRepository.save(entity).toDomain()
    }
}
