package com.sunday.payment.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
class OrderClient(
    @Value("\${clients.order-api.url}") orderApiUrl: String
) {
    private val restClient = RestClient.builder()
        .baseUrl(orderApiUrl)
        .build()

    fun getOrderInfo(orderId: Long): OrderInfo {
        val response = restClient.get()
            .uri("/api/orders/{orderId}", orderId)
            .retrieve()
            .body(OrderResponse::class.java)
            ?: throw RuntimeException("주문 정보를 가져올 수 없습니다: $orderId")

        return OrderInfo(
            orderId = response.id,
            memberId = response.memberId,
            totalAmount = response.totalAmount,
            status = response.status,
            isExpired = LocalDateTime.parse(response.expireAt).isBefore(LocalDateTime.now())
        )
    }

    fun markOrderAsPaid(orderId: Long) {
        restClient.post()
            .uri("/api/orders/{orderId}/mark-paid", orderId)
            .retrieve()
            .toBodilessEntity()
    }

    fun cancelOrder(orderId: Long) {
        restClient.post()
            .uri("/api/orders/{orderId}/cancel", orderId)
            .retrieve()
            .toBodilessEntity()
    }

    private data class OrderResponse(
        val id: Long,
        val memberId: Long,
        val totalAmount: BigDecimal,
        val status: String,
        val expireAt: String
    )
}
