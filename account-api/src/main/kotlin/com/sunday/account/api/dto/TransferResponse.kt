package com.sunday.account.api.dto

import com.sunday.account.domain.Transfer
import java.math.BigDecimal

data class TransferResponse(
    val id: Long,
    val senderMemberId: Long,
    val receiverMemberId: Long,
    val amount: BigDecimal,
    val status: String,
    val description: String?,
    val failureReason: String?,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(transfer: Transfer): TransferResponse = TransferResponse(
            id = transfer.id,
            senderMemberId = transfer.senderMemberId,
            receiverMemberId = transfer.receiverMemberId,
            amount = transfer.amount,
            status = transfer.status.name,
            description = transfer.description,
            failureReason = transfer.failureReason,
            createdAt = transfer.createdAt.toString(),
            updatedAt = transfer.updatedAt.toString()
        )
    }
}
