package com.sunday.account.api.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class AccountTransactionRequest(
    @field:DecimalMin(value = "0.01", message = "거래 금액은 0보다 커야 합니다")
    @field:Digits(integer = 17, fraction = 2, message = "거래 금액은 소수점 둘째 자리까지만 입력할 수 있습니다")
    val amount: BigDecimal,

    @field:Size(max = 500, message = "설명은 500자 이하여야 합니다")
    val description: String? = null,

    @field:Pattern(regexp = ".*\\S.*", message = "작업 ID는 공백일 수 없습니다")
    @field:Size(max = 150, message = "작업 ID는 150자 이하여야 합니다")
    val operationId: String? = null
)
