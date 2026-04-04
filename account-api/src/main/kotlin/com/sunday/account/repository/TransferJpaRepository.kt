package com.sunday.account.repository

import org.springframework.data.jpa.repository.JpaRepository

interface TransferJpaRepository : JpaRepository<TransferJpaEntity, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): TransferJpaEntity?
    fun findBySenderMemberIdOrderByCreatedAtDesc(senderMemberId: Long): List<TransferJpaEntity>
    fun findByReceiverMemberIdOrderByCreatedAtDesc(receiverMemberId: Long): List<TransferJpaEntity>
}
