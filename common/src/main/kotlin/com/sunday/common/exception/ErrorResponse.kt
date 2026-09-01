package com.sunday.common.exception

import java.time.LocalDateTime

data class ErrorResponse(
    val errorCode: String,
    val message: String?,
    val requestId: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun of(errorCode: String, message: String?, requestId: String? = null): ErrorResponse {
            return ErrorResponse(errorCode, message, requestId)
        }
    }
}
