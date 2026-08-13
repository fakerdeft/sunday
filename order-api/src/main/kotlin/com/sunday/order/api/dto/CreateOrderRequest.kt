package com.sunday.order.api.dto

import jakarta.validation.constraints.Positive

data class CreateOrderRequest(
    @field:Positive(message = "상품 ID는 양수여야 합니다")
    val productId: Long,

    @field:Positive(message = "주문 수량은 0보다 커야 합니다")
    val quantity: Int = 1
)
