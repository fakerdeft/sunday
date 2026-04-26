package com.sunday.payment.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Duration

@Component
class AccountApiClient(
    @Value("\${clients.account-api.url}") accountApiUrl: String
) {
    private val restClient = RestClient.builder()
        .baseUrl(accountApiUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(1))
            setReadTimeout(Duration.ofSeconds(3))
        })
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
