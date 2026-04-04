package com.sunday.account.presentation

import com.sunday.account.application.AccountService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

/**
 * 서비스 간 내부 통신용 엔드포인트 (payment-api → account-api)
 */
@RestController
@RequestMapping("/internal/accounts")
class InternalAccountController(
    private val accountService: AccountService
) {

    @PostMapping("/member/{memberId}/withdraw")
    @ResponseStatus(HttpStatus.OK)
    fun withdraw(
        @PathVariable memberId: Long,
        @RequestBody request: InternalTransactionRequest
    ) {
        val account = accountService.getAccountByMemberId(memberId)
        accountService.withdraw(account.id, request.amount, request.description)
    }

    @PostMapping("/member/{memberId}/deposit")
    @ResponseStatus(HttpStatus.OK)
    fun deposit(
        @PathVariable memberId: Long,
        @RequestBody request: InternalTransactionRequest
    ) {
        val account = accountService.getAccountByMemberId(memberId)
        accountService.deposit(account.id, request.amount, request.description)
    }

    data class InternalTransactionRequest(
        val amount: BigDecimal,
        val description: String
    )
}
