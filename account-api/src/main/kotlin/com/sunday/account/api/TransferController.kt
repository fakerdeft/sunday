package com.sunday.account.api

import com.sunday.account.application.TransferService
import com.sunday.account.api.dto.TransferRequest
import com.sunday.account.api.dto.TransferResponse
import com.sunday.common.auth.UserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/transfers")
class TransferController(
    private val transferService: TransferService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun transfer(
        @UserId memberId: Long,
        @Valid @RequestBody request: TransferRequest
    ): TransferResponse {
        val transfer = transferService.transfer(
            senderMemberId = memberId,
            receiverMemberId = request.receiverMemberId,
            amount = request.amount,
            idempotencyKey = request.idempotencyKey,
            description = request.description
        )

        return TransferResponse.from(transfer)
    }

    @GetMapping("/{transferId}")
    @ResponseStatus(HttpStatus.OK)
    fun getTransfer(@PathVariable transferId: Long): TransferResponse {
        return TransferResponse.from(transferService.getTransfer(transferId))
    }

    @GetMapping("/sent")
    @ResponseStatus(HttpStatus.OK)
    fun getSentTransfers(@UserId memberId: Long): List<TransferResponse> {
        return transferService.getSentTransfers(memberId).map { TransferResponse.from(it) }
    }

    @GetMapping("/received")
    @ResponseStatus(HttpStatus.OK)
    fun getReceivedTransfers(@UserId memberId: Long): List<TransferResponse> {
        return transferService.getReceivedTransfers(memberId).map { TransferResponse.from(it) }
    }

    @PostMapping("/{transferId}/reverse")
    @ResponseStatus(HttpStatus.OK)
    fun reverseTransfer(@PathVariable transferId: Long): TransferResponse {
        return TransferResponse.from(transferService.reverseTransfer(transferId))
    }
}
