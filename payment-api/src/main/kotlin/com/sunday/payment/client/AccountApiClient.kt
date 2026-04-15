package com.sunday.payment.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal

@Component
class AccountApiClient(
    @Value("\${clients.account-api.url}") accountApiUrl: String
) {
    private val restClient = RestClient.builder()
        .baseUrl(accountApiUrl)
        .build()

    fun withdraw(memberId: Long, amount: BigDecimal, description: String) {
        restClient.post()
            .uri("/internal/accounts/member/{memberId}/withdraw", memberId)
            .body(TransactionRequest(amount, description))
            .retrieve()
            .toBodilessEntity()
    }

    fun deposit(memberId: Long, amount: BigDecimal, description: String) {
        restClient.post()
            .uri("/internal/accounts/member/{memberId}/deposit", memberId)
            .body(TransactionRequest(amount, description))
            .retrieve()
            .toBodilessEntity()
    }

    data class TransactionRequest(
        val amount: BigDecimal,
        val description: String
    )
}
