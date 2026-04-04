package com.sunday.account.presentation

import com.sunday.account.application.TransferService
import com.sunday.account.presentation.dto.TransferRequest
import com.sunday.account.presentation.dto.TransferResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: TransferRequest
    ): TransferResponse {
        val transfer = transferService.transfer(
            senderMemberId = userId.toLong(),
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
    fun getSentTransfers(@RequestHeader("X-USER-ID") userId: String): List<TransferResponse> {
        return transferService.getSentTransfers(userId.toLong()).map { TransferResponse.from(it) }
    }

    @GetMapping("/received")
    @ResponseStatus(HttpStatus.OK)
    fun getReceivedTransfers(@RequestHeader("X-USER-ID") userId: String): List<TransferResponse> {
        return transferService.getReceivedTransfers(userId.toLong()).map { TransferResponse.from(it) }
    }

    @PostMapping("/{transferId}/reverse")
    @ResponseStatus(HttpStatus.OK)
    fun reverseTransfer(@PathVariable transferId: Long): TransferResponse {
        return TransferResponse.from(transferService.reverseTransfer(transferId))
    }
}
