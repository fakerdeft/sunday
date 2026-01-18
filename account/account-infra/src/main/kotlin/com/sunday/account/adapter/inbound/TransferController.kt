package com.sunday.account.adapter.inbound

import com.sunday.account.adapter.inbound.dto.TransferRequest
import com.sunday.account.adapter.inbound.dto.TransferResponse
import com.sunday.account.application.TransferService
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
    /**
     * 송금
     *
     * 모놀리식 환경: 단일 @Transactional로 ACID 보장
     * MSA 전환 시: Saga 패턴 적용 예정
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun transfer(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: TransferRequest
    ): TransferResponse {
        val senderMemberId = userId.toLong()
        val transfer = transferService.transfer(
            senderMemberId = senderMemberId,
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
        val transfer = transferService.getTransfer(transferId)

        return TransferResponse.from(transfer)
    }

    @GetMapping("/sent")
    @ResponseStatus(HttpStatus.OK)
    fun getSentTransfers(@RequestHeader("X-USER-ID") userId: String): List<TransferResponse> {
        val memberId = userId.toLong()
        val transfers = transferService.getSentTransfers(memberId)

        return transfers.map { TransferResponse.from(it) }
    }

    @GetMapping("/received")
    @ResponseStatus(HttpStatus.OK)
    fun getReceivedTransfers(@RequestHeader("X-USER-ID") userId: String): List<TransferResponse> {
        val memberId = userId.toLong()
        val transfers = transferService.getReceivedTransfers(memberId)

        return transfers.map { TransferResponse.from(it) }
    }

    @PostMapping("/{transferId}/reverse")
    @ResponseStatus(HttpStatus.OK)
    fun reverseTransfer(@PathVariable transferId: Long): TransferResponse {
        val transfer = transferService.reverseTransfer(transferId)

        return TransferResponse.from(transfer)
    }
}
