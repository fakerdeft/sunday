package com.sunday.common.exception

import com.sunday.common.auth.InvalidUserIdException
import com.sunday.common.auth.MissingUserIdException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 예외 처리기 (모든 도메인 공통)
 *
 * 각 도메인에서 발생하는 예외를 일관된 형식으로 처리
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 인증 관련 예외 (X-USER-ID 헤더)
     */
    @ExceptionHandler(MissingUserIdException::class)
    fun handleMissingUserId(e: MissingUserIdException): ResponseEntity<ErrorResponse> {
        log.warn("Missing user ID: {}", e.message)

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of("MISSING_USER_ID", e.message))
    }

    @ExceptionHandler(InvalidUserIdException::class)
    fun handleInvalidUserId(e: InvalidUserIdException): ResponseEntity<ErrorResponse> {
        log.warn("Invalid user ID: {}", e.message)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("INVALID_USER_ID", e.message))
    }

    /**
     * Validation 예외 (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        log.warn("Validation failed: {}", errors)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("VALIDATION_ERROR", errors))
    }

    /**
     * 비즈니스 로직 예외 (IllegalArgumentException, IllegalStateException)
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn("Invalid argument: {}", e.message)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("INVALID_ARGUMENT", e.message))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        log.warn("Invalid state: {}", e.message)

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("INVALID_STATE", e.message))
    }

    /**
     * 동시성 제어 예외
     */
//    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
//    fun handleOptimisticLock(e: ObjectOptimisticLockingFailureException): ResponseEntity<ErrorResponse> {
//        log.warn("Optimistic lock failure: {}", e.message)
//        return ResponseEntity
//            .status(HttpStatus.CONFLICT)
//            .body(ErrorResponse.of("OPTIMISTIC_LOCK_FAILURE", "동시성 충돌이 발생했습니다. 다시 시도해주세요."))
//    }
//
//    @ExceptionHandler(CannotAcquireLockException::class)
//    fun handlePessimisticLock(e: CannotAcquireLockException): ResponseEntity<ErrorResponse> {
//        log.warn("Pessimistic lock timeout: {}", e.message)
//        return ResponseEntity
//            .status(HttpStatus.CONFLICT)
//            .body(ErrorResponse.of("LOCK_TIMEOUT", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."))
//    }
//
//    @ExceptionHandler(DeadlockLoserDataAccessException::class)
//    fun handleDeadlock(e: DeadlockLoserDataAccessException): ResponseEntity<ErrorResponse> {
//        log.warn("Deadlock detected: {}", e.message)
//        return ResponseEntity
//            .status(HttpStatus.CONFLICT)
//            .body(ErrorResponse.of("DEADLOCK", "동시성 충돌이 발생했습니다. 다시 시도해주세요."))
//    }

    /**
     * 예상치 못한 예외
     */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", e)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."))
    }
}
