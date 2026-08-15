package com.sunday.gate.config.exception

import com.sunday.common.exception.ErrorResponse
import com.sunday.gate.config.auth.InvalidUserIdException
import com.sunday.gate.config.auth.MissingUserIdException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.RestClientException

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

    @ExceptionHandler(RestClientException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleOrderApiFailure(e: RestClientException): ErrorResponse {
        log.error("주문 서버와 통신하지 못했습니다", e)

        return ErrorResponse.of("ORDER_API_UNAVAILABLE", "잠시 후 다시 시도해 주세요.", requestId())
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(e: IllegalArgumentException): ErrorResponse {
        log.warn("유효하지 않은 인자입니다: {}", e.message)

        return ErrorResponse.of("INVALID_ARGUMENT", e.message, requestId())
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(e: Exception): ErrorResponse {
        log.error("예상하지 못한 오류가 발생했습니다", e)

        return ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.", requestId())
    }

    private fun requestId(): String? = MDC.get("requestId")
}
