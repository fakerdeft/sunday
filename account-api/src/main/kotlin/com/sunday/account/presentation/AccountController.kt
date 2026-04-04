package com.sunday.account.presentation

import com.sunday.account.application.AccountService
import com.sunday.account.presentation.dto.AccountResponse
import com.sunday.account.presentation.dto.CreateAccountRequest
import com.sunday.account.presentation.dto.DepositRequest
import com.sunday.account.presentation.dto.TransactionResponse
import com.sunday.account.presentation.dto.WithdrawRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val accountService: AccountService
) {

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun getMyAccount(@RequestHeader("X-USER-ID") userId: String): AccountResponse {
        return AccountResponse.from(accountService.getAccountByUserId(userId))
    }

    @PostMapping("/me/deposit")
    @ResponseStatus(HttpStatus.OK)
    fun deposit(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: DepositRequest
    ): AccountResponse {
        val account = accountService.getAccountByUserId(userId)
        return AccountResponse.from(accountService.deposit(account.id, request.amount, request.description))
    }

    @PostMapping("/me/withdraw")
    @ResponseStatus(HttpStatus.OK)
    fun withdraw(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: WithdrawRequest
    ): AccountResponse {
        val account = accountService.getAccountByUserId(userId)
        return AccountResponse.from(accountService.withdraw(account.id, request.amount, request.description))
    }

    @GetMapping("/me/transactions")
    @ResponseStatus(HttpStatus.OK)
    fun getTransactionHistory(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): List<TransactionResponse> {
        val account = accountService.getAccountByUserId(userId)
        return accountService.getTransactionHistory(account.id, page, size).map { TransactionResponse.from(it) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createAccount(@RequestBody request: CreateAccountRequest): AccountResponse {
        return AccountResponse.from(accountService.createAccount(request.memberId, request.userId))
    }
}
