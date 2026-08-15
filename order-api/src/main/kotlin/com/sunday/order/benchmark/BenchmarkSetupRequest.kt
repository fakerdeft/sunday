package com.sunday.order.benchmark

import jakarta.validation.constraints.Positive

data class BenchmarkSetupRequest(
    @field:Positive(message = "상품 ID는 양수여야 합니다")
    val productId: Long,

    @field:Positive(message = "재고 수량은 0보다 커야 합니다")
    val quantity: Int
)
