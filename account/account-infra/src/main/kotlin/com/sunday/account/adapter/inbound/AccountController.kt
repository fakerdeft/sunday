package com.sunday.account.adapter.inbound

import com.sunday.account.application.AccountService
import com.sunday.account.domain.Account
import com.sunday.account.domain.AccountTransaction
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

/**
 * Account REST Controller
 */
@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val accountService: AccountService
) {
    @GetMapping("/me")
    fun getMyAccount(
        @RequestHeader("X-USER-ID") userId: String
    ): ResponseEntity<AccountResponse> {
        val account = accountService.getAccountByUserId(userId)
        return ResponseEntity.ok(AccountResponse.from(account))
    }

    @PostMapping("/me/deposit")
    fun deposit(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: DepositRequest
    ): ResponseEntity<AccountResponse> {
        val account = accountService.getAccountByUserId(userId)
        val updatedAccount = accountService.deposit(account.id, request.amount, request.description)
        return ResponseEntity.ok(AccountResponse.from(updatedAccount))
    }

    @PostMapping("/me/withdraw")
    fun withdraw(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: WithdrawRequest
    ): ResponseEntity<AccountResponse> {
        val account = accountService.getAccountByUserId(userId)
        val updatedAccount = accountService.withdraw(account.id, request.amount, request.description)
        return ResponseEntity.ok(AccountResponse.from(updatedAccount))
    }

    @GetMapping("/me/transactions")
    fun getTransactionHistory(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<List<TransactionResponse>> {
        val account = accountService.getAccountByUserId(userId)
        val transactions = accountService.getTransactionHistory(account.id, page, size)
        return ResponseEntity.ok(transactions.map { TransactionResponse.from(it) })
    }

    @PostMapping
    fun createAccount(
        @RequestBody request: CreateAccountRequest
    ): ResponseEntity<AccountResponse> {
        val account = accountService.createAccount(request.memberId, request.userId)
        return ResponseEntity.ok(AccountResponse.from(account))
    }
}

// ===== Request DTOs =====

data class CreateAccountRequest(
    val memberId: Long,
    val userId: String
)

data class DepositRequest(
    val amount: BigDecimal,
    val description: String? = null
)

data class WithdrawRequest(
    val amount: BigDecimal,
    val description: String? = null
)

// ===== Response DTOs =====

data class AccountResponse(
    val id: Long,
    val memberId: Long,
    val userId: String,
    val balance: BigDecimal,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(account: Account): AccountResponse {
            return AccountResponse(
                id = account.id,
                memberId = account.memberId,
                userId = account.userId,
                balance = account.balance,
                createdAt = account.createdAt.toString(),
                updatedAt = account.updatedAt.toString()
            )
        }
    }
}

data class TransactionResponse(
    val id: Long,
    val transactionType: String,
    val amount: BigDecimal,
    val balanceAfter: BigDecimal,
    val description: String?,
    val createdAt: String
) {
    companion object {
        fun from(transaction: AccountTransaction): TransactionResponse {
            return TransactionResponse(
                id = transaction.id,
                transactionType = transaction.transactionType.name,
                amount = transaction.amount,
                balanceAfter = transaction.balanceAfter,
                description = transaction.description,
                createdAt = transaction.createdAt.toString()
            )
        }
    }
}
