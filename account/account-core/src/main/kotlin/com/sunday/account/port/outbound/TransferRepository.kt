package com.sunday.account.port.outbound

import com.sunday.account.domain.Transfer

/**
 * Transfer Repository (Output Port)
 */
interface TransferRepository {
    fun findById(id: Long): Transfer?
    fun findByIdempotencyKey(idempotencyKey: String): Transfer?
    fun findBySenderMemberId(memberId: Long): List<Transfer>
    fun findByReceiverMemberId(memberId: Long): List<Transfer>
    fun save(transfer: Transfer): Transfer
}
