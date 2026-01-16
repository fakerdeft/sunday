package com.sunday.account.exception

import java.math.BigDecimal

/**
 * Account 도메인 예외 (Core에서 정의)
 */
sealed class AccountException(message: String) : RuntimeException(message)

class AccountNotFoundException(id: Long) :
    AccountException("Account not found: $id")

class AccountNotFoundByMemberException(memberId: Long) :
    AccountException("Account not found for member: $memberId")

class AccountNotFoundByUserIdException(userId: String) :
    AccountException("Account not found for user: $userId")

class InsufficientBalanceException(current: BigDecimal, requested: BigDecimal) :
    AccountException("Insufficient balance. Current: $current, Requested: $requested")

class AccountAlreadyExistsException(memberId: Long) :
    AccountException("Account already exists for member: $memberId")

class ConcurrentModificationException(accountId: Long) :
    AccountException("Account $accountId was modified by another transaction. Please retry.")
