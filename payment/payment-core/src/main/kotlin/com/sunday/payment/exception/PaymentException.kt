package com.sunday.payment.exception

import com.sunday.common.exception.AlreadyExistsException
import com.sunday.common.exception.DuplicateRequestException
import com.sunday.common.exception.InsufficientBalanceException
import com.sunday.common.exception.LockAcquisitionException
import com.sunday.common.exception.NotFoundException
import com.sunday.common.exception.OrderNotPayableException
import com.sunday.common.exception.PaymentFailedException
import java.math.BigDecimal

/**
 * Payment 도메인 예외
 */
sealed class PaymentException(message: String) : RuntimeException(message)

class PaymentNotFoundException(paymentId: Long) :
    PaymentException("결제 정보를 찾을 수 없습니다: $paymentId"),
    NotFoundException

class PaymentNotFoundByOrderException(orderId: Long) :
    PaymentException("해당 주문의 결제 정보를 찾을 수 없습니다: $orderId"),
    NotFoundException

class DuplicatePaymentException(idempotencyKey: String) :
    PaymentException("중복된 결제 요청입니다. (키: $idempotencyKey)"),
    DuplicateRequestException

class PaymentAlreadyCompletedException(orderId: Long) :
    PaymentException("이미 결제가 완료된 주문입니다: $orderId"),
    AlreadyExistsException

class PaymentFailedException(orderId: Long, reason: String) :
    PaymentException("주문 $orderId 결제 실패: $reason"),
    PaymentFailedException

class InsufficientBalanceForPaymentException(required: BigDecimal, available: BigDecimal) :
    PaymentException("결제 잔액이 부족합니다. 필요: $required, 보유: $available"),
    InsufficientBalanceException

class PaymentLockAcquisitionException(orderId: Long) :
    PaymentException("주문 $orderId 에 대한 결제 락 획득에 실패했습니다."),
    LockAcquisitionException

class OrderNotPayableException(orderId: Long, reason: String) :
    PaymentException("주문 $orderId 는 결제 가능한 상태가 아닙니다: $reason"),
    OrderNotPayableException

class PaymentNotRefundableException(paymentId: Long, currentStatus: String) :
    PaymentException("결제 $paymentId 를 환불할 수 없습니다. 현재 상태: $currentStatus")

class PaymentNotCompletableException(paymentId: Long, currentStatus: String) :
    PaymentException("결제 $paymentId 를 완료 처리할 수 없습니다. 현재 상태: $currentStatus")

class PaymentNotFailableException(paymentId: Long, currentStatus: String) :
    PaymentException("결제 $paymentId 를 실패 처리할 수 없습니다. 현재 상태: $currentStatus")

class InvalidPaymentAmountException(amount: BigDecimal) :
    PaymentException("결제 금액은 0보다 커야 합니다. 입력값: $amount")

class InvalidIdempotencyKeyException :
    PaymentException("멱등성 키는 비어있을 수 없습니다.")
