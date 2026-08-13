package com.sunday.account.api

import com.sunday.account.application.AccountService
import com.sunday.account.api.dto.AccountOperationResponse
import com.sunday.account.api.dto.AccountResponse
import com.sunday.account.api.dto.AccountTransactionRequest
import com.sunday.account.api.dto.CreateAccountRequest
import com.sunday.account.api.dto.TransactionHistoryRequest
import com.sunday.account.api.dto.TransactionResponse
import com.sunday.common.auth.UserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val accountService: AccountService
) {

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun getMyAccount(@UserId memberId: Long): AccountResponse {
        return AccountResponse.from(accountService.getAccountByMemberId(memberId))
    }

    @GetMapping("/operations/{operationId}")
    @ResponseStatus(HttpStatus.OK)
    fun getOperation(@PathVariable operationId: String): AccountOperationResponse {
        return AccountOperationResponse.from(accountService.findOperation(operationId))
    }

    @PostMapping("/me/deposit")
    @ResponseStatus(HttpStatus.OK)
    fun deposit(
        @UserId memberId: Long,
        @Valid @RequestBody request: AccountTransactionRequest
    ): AccountResponse {
        return AccountResponse.from(
            accountService.depositForMember(
                memberId = memberId,
                amount = request.amount,
                description = request.description,
                operationId = request.operationId
            )
        )
    }

    @PostMapping("/me/withdraw")
    @ResponseStatus(HttpStatus.OK)
    fun withdraw(
        @UserId memberId: Long,
        @Valid @RequestBody request: AccountTransactionRequest
    ): AccountResponse {
        return AccountResponse.from(
            accountService.withdrawForMember(
                memberId = memberId,
                amount = request.amount,
                description = request.description,
                operationId = request.operationId
            )
        )
    }

    @GetMapping("/me/transactions")
    @ResponseStatus(HttpStatus.OK)
    fun getTransactionHistory(
        @UserId memberId: Long,
        @Valid @ModelAttribute request: TransactionHistoryRequest
    ): List<TransactionResponse> {
        return accountService.getTransactionHistoryForMember(memberId, request.page, request.size)
            .map { TransactionResponse.from(it) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createAccount(@Valid @RequestBody request: CreateAccountRequest): AccountResponse {
        return AccountResponse.from(accountService.createAccount(request.memberId, request.userId))
    }
}
