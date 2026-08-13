package com.sunday.account.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreateAccountRequest(
    @field:Positive(message = "회원 ID는 양수여야 합니다")
    val memberId: Long,

    @field:NotBlank(message = "계정 식별자는 필수입니다")
    @field:Size(max = 100, message = "계정 식별자는 100자 이하여야 합니다")
    val userId: String
)
