package com.sunday.payment.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
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

    fun getOperation(operationId: String): OperationInfo =
        restClient.get()
            .uri("/api/accounts/operations/{operationId}", operationId)
            .retrieve()
            .body<OperationInfo>()
            ?: throw RuntimeException("계좌 작업 상태를 확인할 수 없습니다: $operationId")

    fun withdraw(memberId: Long, amount: BigDecimal, description: String, operationId: String) {
        restClient.post()
            .uri("/api/accounts/me/withdraw")
            .header(USER_ID_HEADER, memberId.toString())
            .body(TransactionRequest(amount, description, operationId))
            .retrieve()
            .toBodilessEntity()
    }

    fun deposit(memberId: Long, amount: BigDecimal, description: String, operationId: String) {
        restClient.post()
            .uri("/api/accounts/me/deposit")
            .header(USER_ID_HEADER, memberId.toString())
            .body(TransactionRequest(amount, description, operationId))
            .retrieve()
            .toBodilessEntity()
    }

    private companion object {
        const val USER_ID_HEADER = "X-USER-ID"
    }
}
