package com.sunday.common.exception

import com.sunday.common.auth.InvalidUserIdException
import com.sunday.common.auth.MissingUserIdException
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 예외 처리기 (모든 도메인 공통)
 *
 * 각 도메인에서 발생하는 예외를 일관된 형식으로 처리
 * @ResponseStatus를 사용하여 HTTP 상태 코드 매핑
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    // ==================== 인증 관련 ====================

    @ExceptionHandler(MissingUserIdException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleMissingUserId(e: MissingUserIdException): ErrorResponse {
        log.warn("Missing user ID: {}", e.message)

        return ErrorResponse.of("MISSING_USER_ID", e.message)
    }

    @ExceptionHandler(InvalidUserIdException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidUserId(e: InvalidUserIdException): ErrorResponse {
        log.warn("Invalid user ID: {}", e.message)

        return ErrorResponse.of("INVALID_USER_ID", e.message)
    }

    // ==================== 도메인 공통 예외 처리 ====================
    // 인터페이스 기반 예외 처리를 위해 RuntimeException을 잡아서 분기 처리

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(e: RuntimeException, response: HttpServletResponse): ErrorResponse {
        return when (e) {
            is NotFoundException -> {
                log.warn("Resource not found: {}", e.message)
                response.status = HttpStatus.NOT_FOUND.value()
                ErrorResponse.of("NOT_FOUND", e.message)
            }

            is InsufficientBalanceException -> {
                log.warn("Insufficient balance: {}", e.message)
                response.status = HttpStatus.BAD_REQUEST.value()
                ErrorResponse.of("INSUFFICIENT_BALANCE", e.message)
            }

            is AlreadyExistsException -> {
                log.warn("Resource already exists: {}", e.message)
                response.status = HttpStatus.CONFLICT.value()
                ErrorResponse.of("ALREADY_EXISTS", e.message)
            }

            is DuplicateRequestException -> {
                log.warn("Duplicate request: {}", e.message)
                response.status = HttpStatus.CONFLICT.value()
                ErrorResponse.of("ALREADY_EXISTS", e.message)
            }

            is ConcurrencyException -> {
                log.warn("Concurrency conflict: {}", e.message)
                response.status = HttpStatus.CONFLICT.value()
                ErrorResponse.of("CONCURRENCY_CONFLICT", e.message)
            }

            is LockAcquisitionException -> {
                log.warn("Lock acquisition failed: {}", e.message)
                response.status = HttpStatus.SERVICE_UNAVAILABLE.value()
                ErrorResponse.of("LOCK_ACQUISITION_FAILED", e.message)
            }

            is OutOfStockException -> {
                log.warn("Out of stock: {}", e.message)
                response.status = HttpStatus.CONFLICT.value()
                ErrorResponse.of("OUT_OF_STOCK", e.message)
            }

            is HotDealNotActiveException -> {
                log.warn("Hot deal not active: {}", e.message)
                response.status = HttpStatus.BAD_REQUEST.value()
                ErrorResponse.of("HOT_DEAL_NOT_ACTIVE", e.message)
            }

            is InvalidOrderStatusException -> {
                log.warn("Invalid order status: {}", e.message)
                response.status = HttpStatus.BAD_REQUEST.value()
                ErrorResponse.of("INVALID_ORDER_STATUS", e.message)
            }

            is OrderNotPayableException -> {
                log.warn("Order not payable: {}", e.message)
                response.status = HttpStatus.BAD_REQUEST.value()
                ErrorResponse.of("INVALID_ORDER_STATUS", e.message)
            }

            is PaymentFailedException -> {
                log.warn("Payment failed: {}", e.message)
                response.status = HttpStatus.BAD_REQUEST.value()
                ErrorResponse.of("PAYMENT_FAILED", e.message)
            }

            else -> {
                // 처리되지 않은 다른 RuntimeException은 500 에러로 처리
                log.error("Unexpected runtime error", e)
                response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.")
            }
        }
    }

    // ==================== Validation ====================

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationException(e: MethodArgumentNotValidException): ErrorResponse {
        val errors = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation failed: {}", errors)

        return ErrorResponse.of("VALIDATION_ERROR", errors)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(e: IllegalArgumentException): ErrorResponse {
        log.warn("Invalid argument: {}", e.message)

        return ErrorResponse.of("INVALID_ARGUMENT", e.message)
    }

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleIllegalState(e: IllegalStateException): ErrorResponse {
        log.warn("Invalid state: {}", e.message)

        return ErrorResponse.of("INVALID_STATE", e.message)
    }

    // ==================== Fallback ====================

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(e: Exception): ErrorResponse {
        log.error("Unexpected error", e)

        return ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.")
    }
}
