package com.sunday.payment.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.LocalDateTime

@Component
class OrderApiClient(
    @Value("\${clients.order-api.url}") orderApiUrl: String
) {
    private val restClient = RestClient.builder()
        .baseUrl(orderApiUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(1))
            setReadTimeout(Duration.ofSeconds(3))
        })
        .build()

    fun getReservationInfo(reservationId: Long): ReservationInfo {
        val response = restClient.get()
            .uri("/api/orders/reservations/{reservationId}", reservationId)
            .retrieve()
            .body(ReservationResponse::class.java)
            ?: throw RuntimeException("선점 정보를 가져올 수 없습니다: $reservationId")

        return ReservationInfo(
            reservationId = response.id,
            memberId = response.memberId,
            totalAmount = response.totalAmount,
            status = response.status,
            isExpired = LocalDateTime.parse(response.expireAt).isBefore(LocalDateTime.now())
        )
    }

    /** 결제 성공 → 확정 주문 생성 (선점 → PAID Order) */
    fun confirmReservation(reservationId: Long) {
        restClient.post()
            .uri("/api/orders/reservations/{reservationId}/confirm", reservationId)
            .retrieve()
            .toBodilessEntity()
    }

    /** 결제 실패 → 선점 취소 (재고 복구 O) */
    fun cancelReservation(reservationId: Long) {
        restClient.post()
            .uri("/api/orders/reservations/{reservationId}/cancel", reservationId)
            .retrieve()
            .toBodilessEntity()
    }

    /** 환불 → 확정 주문 취소 (재고 복구 X) */
    fun cancelOrder(reservationId: Long) {
        restClient.post()
            .uri("/api/orders/{reservationId}/cancel", reservationId)
            .retrieve()
            .toBodilessEntity()
    }
}
