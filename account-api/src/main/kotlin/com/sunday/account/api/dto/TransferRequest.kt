package com.sunday.account.api.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class TransferRequest(
    @field:Positive(message = "받는 회원 ID는 양수여야 합니다")
    val receiverMemberId: Long,

    @field:DecimalMin(value = "0.01", message = "송금액은 0보다 커야 합니다")
    @field:Digits(integer = 17, fraction = 2, message = "송금액은 소수점 둘째 자리까지만 입력할 수 있습니다")
    val amount: BigDecimal,

    @field:NotBlank(message = "멱등성 키는 필수입니다")
    @field:Size(max = 100, message = "멱등성 키는 100자 이하여야 합니다")
    val idempotencyKey: String,

    @field:Size(max = 500, message = "설명은 500자 이하여야 합니다")
    val description: String? = null
)
