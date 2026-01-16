package com.sunday.account.domain

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Account 도메인 모델 (예치금 계좌)
 *
 * - 순수 비즈니스 로직 (Spring 의존성 없음)
 * - 불변성 보장 (data class + copy)
 * - 낙관적 락을 위한 version 필드 포함
 */
data class Account(
    val id: Long,
    val memberId: Long,
    val userId: String,
    val balance: BigDecimal,
    val version: Long,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(balance >= BigDecimal.ZERO) { "Balance cannot be negative" }
    }

    companion object {
        /**
         * 새로운 Account 생성 (ID는 DB에서 자동 생성)
         */
        fun create(memberId: Long, userId: String): Account {
            return Account(
                id = 0L,
                memberId = memberId,
                userId = userId,
                balance = BigDecimal.ZERO,
                version = 0L
            )
        }
    }

    /**
     * 잔액 충전 (deposit)
     * @return 갱신된 Account와 생성된 거래 이력
     */
    fun deposit(amount: BigDecimal, description: String? = null): Pair<Account, AccountTransaction> {
        require(amount > BigDecimal.ZERO) { "Deposit amount must be positive" }

        val newBalance = balance + amount
        val updatedAccount = this.copy(
            balance = newBalance,
            updatedAt = LocalDateTime.now()
        )

        val transaction = AccountTransaction.create(
            accountId = this.id,
            type = TransactionType.DEPOSIT,
            amount = amount,
            balanceAfter = newBalance,
            description = description ?: "예치금 충전"
        )

        return updatedAccount to transaction
    }

    /**
     * 잔액 차감 (withdraw)
     * @return 갱신된 Account와 생성된 거래 이력
     * @throws IllegalStateException 잔액 부족 시
     */
    fun withdraw(amount: BigDecimal, description: String? = null): Pair<Account, AccountTransaction> {
        require(amount > BigDecimal.ZERO) { "Withdrawal amount must be positive" }
        check(balance >= amount) { "Insufficient balance. Current: $balance, Requested: $amount" }

        val newBalance = balance - amount
        val updatedAccount = this.copy(
            balance = newBalance,
            updatedAt = LocalDateTime.now()
        )

        val transaction = AccountTransaction.create(
            accountId = this.id,
            type = TransactionType.WITHDRAWAL,
            amount = amount,
            balanceAfter = newBalance,
            description = description ?: "예치금 차감"
        )

        return updatedAccount to transaction
    }

    /**
     * 잔액 차감 가능 여부 확인
     */
    fun canWithdraw(amount: BigDecimal): Boolean {
        return balance >= amount
    }
}
