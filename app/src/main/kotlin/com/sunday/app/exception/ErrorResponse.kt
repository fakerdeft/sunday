package com.sunday.app.exception

import java.time.LocalDateTime

/**
 * 공통 에러 응답 DTO
 *
 * 모든 도메인의 에러 응답 형식을 통일
 */
data class ErrorResponse(
    val errorCode: String,
    val message: String?,
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun of(errorCode: String, message: String?): ErrorResponse {
            return ErrorResponse(errorCode, message)
        }
    }
}
