package com.sunday.payment.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class ProcessPaymentRequest(
    @field:Positive(message = "주문 ID는 양수여야 합니다")
    val orderId: Long,

    @field:NotBlank(message = "멱등성 키는 필수입니다")
    @field:Size(max = 100, message = "멱등성 키는 100자 이하여야 합니다")
    val idempotencyKey: String
)
