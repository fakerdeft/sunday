package com.sunday.account.config.exception

import com.sunday.account.config.auth.InvalidUserIdException
import com.sunday.account.config.auth.MissingUserIdException
import com.sunday.common.exception.AlreadyExistsException
import com.sunday.common.exception.ConcurrencyException
import com.sunday.common.exception.DuplicateRequestException
import com.sunday.common.exception.ErrorResponse
import com.sunday.common.exception.InsufficientBalanceException
import com.sunday.common.exception.LockAcquisitionException
import com.sunday.common.exception.NotFoundException
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MissingUserIdException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleMissingUserId(e: MissingUserIdException): ErrorResponse {
        log.warn("회원 ID가 누락되었습니다: {}", e.message)

        return ErrorResponse.of("MISSING_USER_ID", e.message, requestId())
    }

    @ExceptionHandler(InvalidUserIdException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidUserId(e: InvalidUserIdException): ErrorResponse {
        log.warn("유효하지 않은 회원 ID입니다: {}", e.message)

        return ErrorResponse.of("INVALID_USER_ID", e.message, requestId())
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(e: RuntimeException, response: HttpServletResponse): ErrorResponse {
        return when (e) {
            is NotFoundException -> {
                log.warn("리소스를 찾을 수 없습니다: {}", e.message)
                response.status = HttpStatus.NOT_FOUND.value()
                ErrorResponse.of("NOT_FOUND", e.message, requestId())
            }
            is InsufficientBalanceException -> {
                log.warn("잔액이 부족합니다: {}", e.message)
                response.status = HttpStatus.BAD_REQUEST.value()
                ErrorResponse.of("INSUFFICIENT_BALANCE", e.message, requestId())
            }
            is AlreadyExistsException -> {
                log.warn("이미 존재하는 리소스입니다: {}", e.message)
                response.status = HttpStatus.CONFLICT.value()
                ErrorResponse.of("ALREADY_EXISTS", e.message, requestId())
            }
            is DuplicateRequestException -> {
                log.warn("중복 요청입니다: {}", e.message)
                response.status = HttpStatus.CONFLICT.value()
                ErrorResponse.of("ALREADY_EXISTS", e.message, requestId())
            }
            is ConcurrencyException -> {
                log.warn("동시성 충돌이 발생했습니다: {}", e.message)
                response.status = HttpStatus.CONFLICT.value()
                ErrorResponse.of("CONCURRENCY_CONFLICT", e.message, requestId())
            }
            is LockAcquisitionException -> {
                log.warn("락 획득에 실패했습니다: {}", e.message)
                response.status = HttpStatus.SERVICE_UNAVAILABLE.value()
                ErrorResponse.of("LOCK_ACQUISITION_FAILED", e.message, requestId())
            }
            else -> {
                log.error("예상하지 못한 런타임 오류가 발생했습니다", e)
                response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.", requestId())
            }
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationException(e: MethodArgumentNotValidException): ErrorResponse {
        val errors = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        log.warn("요청값 검증에 실패했습니다: {}", errors)

        return ErrorResponse.of("VALIDATION_ERROR", errors, requestId())
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(e: IllegalArgumentException): ErrorResponse {
        log.warn("유효하지 않은 인자입니다: {}", e.message)

        return ErrorResponse.of("INVALID_ARGUMENT", e.message, requestId())
    }

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleIllegalState(e: IllegalStateException): ErrorResponse {
        log.warn("유효하지 않은 상태입니다: {}", e.message)

        return ErrorResponse.of("INVALID_STATE", e.message, requestId())
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(e: Exception): ErrorResponse {
        log.error("예상하지 못한 오류가 발생했습니다", e)

        return ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.", requestId())
    }

    private fun requestId(): String? = MDC.get("requestId")
}
