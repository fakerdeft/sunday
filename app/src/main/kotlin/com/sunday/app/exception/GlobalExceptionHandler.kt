package com.sunday.app.exception

import com.sunday.app.auth.InvalidUserIdException
import com.sunday.app.auth.MissingUserIdException
import com.sunday.common.exception.AlreadyExistsException
import com.sunday.common.exception.ConcurrencyException
import com.sunday.common.exception.DuplicateRequestException
import com.sunday.common.exception.ErrorResponse
import com.sunday.common.exception.HotDealNotActiveException
import com.sunday.common.exception.InsufficientBalanceException
import com.sunday.common.exception.InvalidOrderStatusException
import com.sunday.common.exception.LockAcquisitionException
import com.sunday.common.exception.NotFoundException
import com.sunday.common.exception.OrderNotPayableException
import com.sunday.common.exception.OutOfStockException
import com.sunday.common.exception.PaymentFailedException
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
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
class GlobalExceptionHandler(
    private val meterRegistry: MeterRegistry
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun requestId(): String? = MDC.get("requestId")

    private fun countError(type: String) {
        meterRegistry.counter("domain.error", "type", type).increment()
    }

    // ==================== 인증 관련 ====================

    @ExceptionHandler(MissingUserIdException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleMissingUserId(e: MissingUserIdException): ErrorResponse {
        log.warn("Missing user ID: {}", e.message)
        countError("missing_user_id")

        return ErrorResponse.of("MISSING_USER_ID", e.message, requestId())
    }

    @ExceptionHandler(InvalidUserIdException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidUserId(e: InvalidUserIdException): ErrorResponse {
        log.warn("Invalid user ID: {}", e.message)
        countError("invalid_user_id")

        return ErrorResponse.of("INVALID_USER_ID", e.message, requestId())
    }

    // ==================== 도메인 공통 예외 처리 ====================

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(e: RuntimeException, response: HttpServletResponse): ErrorResponse {
        return when (e) {
            is NotFoundException -> {
                log.warn("Resource not found: {}", e.message)
                countError("not_found")
                response.status = HttpStatus.NOT_FOUND.value()
                ErrorResponse.of("NOT_FOUND", e.message, requestId())
            }

            is InsufficientBalanceException -> {
                log.warn("Insufficient balance: {}", e.message)
                countError("insufficient_balance")
                response.status = HttpStatus.BAD_REQUEST.value()
                ErrorResponse.of("INSUFFICIENT_BALANCE", e.message, requestId())
            }

            is AlreadyExistsException -> {
                log.warn("Resource already exists: {}", e.message)
                countError("already_exists")
                response.status = HttpStatus.CONFLICT.value()
                ErrorResponse.of("ALREADY_EXISTS", e.message, requestId())
            }

            is DuplicateRequestException -> {
                log.warn("Duplicate request: {}", e.message)
                countError("duplicate_request")
                response.status = HttpStatus.CONFLICT.value()
                ErrorResponse.of("ALREADY_EXISTS", e.message, requestId())
            }

            is ConcurrencyException -> {
                log.warn("Concurrency conflict: {}", e.message)
                countError("concurrency_conflict")
                response.status = HttpStatus.CONFLICT.value()
                ErrorResponse.of("CONCURRENCY_CONFLICT", e.message, requestId())
            }

            is LockAcquisitionException -> {
                log.warn("Lock acquisition failed: {}", e.message)
                countError("lock_acquisition_failed")
                response.status = HttpStatus.SERVICE_UNAVAILABLE.value()
                ErrorResponse.of("LOCK_ACQUISITION_FAILED", e.message, requestId())
            }

            is OutOfStockException -> {
                log.warn("Out of stock: {}", e.message)
                countError("out_of_stock")
                response.status = HttpStatus.CONFLICT.value()
                ErrorResponse.of("OUT_OF_STOCK", e.message, requestId())
            }

            is HotDealNotActiveException -> {
                log.warn("Hot deal not active: {}", e.message)
                countError("hot_deal_not_active")
                response.status = HttpStatus.BAD_REQUEST.value()
                ErrorResponse.of("HOT_DEAL_NOT_ACTIVE", e.message, requestId())
            }

            is InvalidOrderStatusException -> {
                log.warn("Invalid order status: {}", e.message)
                countError("invalid_order_status")
                response.status = HttpStatus.BAD_REQUEST.value()
                ErrorResponse.of("INVALID_ORDER_STATUS", e.message, requestId())
            }

            is OrderNotPayableException -> {
                log.warn("Order not payable: {}", e.message)
                countError("order_not_payable")
                response.status = HttpStatus.BAD_REQUEST.value()
                ErrorResponse.of("INVALID_ORDER_STATUS", e.message, requestId())
            }

            is PaymentFailedException -> {
                log.warn("Payment failed: {}", e.message)
                countError("payment_failed")
                response.status = HttpStatus.BAD_REQUEST.value()
                ErrorResponse.of("PAYMENT_FAILED", e.message, requestId())
            }

            else -> {
                log.error("Unexpected runtime error", e)
                countError("unexpected")
                response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.", requestId())
            }
        }
    }

    // ==================== Validation ====================

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationException(e: MethodArgumentNotValidException): ErrorResponse {
        val errors = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation failed: {}", errors)
        countError("validation")

        return ErrorResponse.of("VALIDATION_ERROR", errors, requestId())
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(e: IllegalArgumentException): ErrorResponse {
        log.warn("Invalid argument: {}", e.message)
        countError("invalid_argument")

        return ErrorResponse.of("INVALID_ARGUMENT", e.message, requestId())
    }

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleIllegalState(e: IllegalStateException): ErrorResponse {
        log.warn("Invalid state: {}", e.message)
        countError("invalid_state")

        return ErrorResponse.of("INVALID_STATE", e.message, requestId())
    }

    // ==================== Fallback ====================

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(e: Exception): ErrorResponse {
        log.error("Unexpected error", e)
        countError("unexpected")

        return ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.", requestId())
    }
}
