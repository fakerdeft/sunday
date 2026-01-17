package com.sunday.account.exception

import com.sunday.common.exception.AlreadyExistsException
import com.sunday.common.exception.ConcurrencyException
import com.sunday.common.exception.DuplicateRequestException
import com.sunday.common.exception.InsufficientBalanceException
import com.sunday.common.exception.LockAcquisitionException
import com.sunday.common.exception.NotFoundException
import java.math.BigDecimal

/**
 * Account 도메인 예외 (Core에서 정의)
 */
sealed class AccountException(message: String) : RuntimeException(message)

class AccountNotFoundException(id: Long) :
    AccountException("계좌를 찾을 수 없습니다: $id"),
    NotFoundException

class AccountNotFoundByMemberException(memberId: Long) :
    AccountException("해당 회원의 계좌를 찾을 수 없습니다: $memberId"),
    NotFoundException

class AccountNotFoundByUserIdException(userId: String) :
    AccountException("해당 사용자의 계좌를 찾을 수 없습니다: $userId"),
    NotFoundException

class InsufficientBalanceException(current: BigDecimal, requested: BigDecimal) :
    AccountException("잔액이 부족합니다. 현재: $current, 요청: $requested"),
    InsufficientBalanceException

class AccountAlreadyExistsException(memberId: Long) :
    AccountException("해당 회원의 계좌가 이미 존재합니다: $memberId"),
    AlreadyExistsException

class ConcurrentModificationException(accountId: Long) :
    AccountException("계좌 $accountId 가 다른 트랜잭션에 의해 수정되었습니다. 다시 시도해주세요."),
    ConcurrencyException

class InvalidAccountUserIdException :
    AccountException("User ID는 공백이 불가능합니다.")

class InvalidAccountBalanceException(balance: BigDecimal) :
    AccountException("잔액은 음수가 될 수 없습니다. 입력값: $balance")

class InvalidTransactionAmountException(amount: BigDecimal) :
    AccountException("거래 금액은 양수여야 합니다. 입력값: $amount")

// ===== Transfer 관련 예외 =====

class TransferNotFoundException(id: Long) :
    AccountException("이체 내역을 찾을 수 없습니다: $id"),
    NotFoundException

class DuplicateTransferException(idempotencyKey: String) :
    AccountException("중복된 이체 요청입니다. (키: $idempotencyKey)"),
    DuplicateRequestException

class TransferToSelfException :
    AccountException("자신에게 이체할 수 없습니다.")

class TransferLockAcquisitionException(senderAccountId: Long, receiverAccountId: Long) :
    AccountException("이체 락 획득에 실패했습니다. (보내는 분: $senderAccountId, 받는 분: $receiverAccountId)"),
    LockAcquisitionException

class TransferFailedException(transferId: Long, reason: String) :
    AccountException("이체 $transferId 실패: $reason")

class TransferNotReversibleException(transferId: Long, currentStatus: String) :
    AccountException("이체 $transferId 를 취소할 수 없습니다. 현재 상태: $currentStatus")
