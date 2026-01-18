package com.sunday.account.adapter.outbound

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransferJpaRepository : JpaRepository<TransferJpaEntity, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): TransferJpaEntity?
    fun findBySenderMemberIdOrderByCreatedAtDesc(senderMemberId: Long): List<TransferJpaEntity>
    fun findByReceiverMemberIdOrderByCreatedAtDesc(receiverMemberId: Long): List<TransferJpaEntity>
}
