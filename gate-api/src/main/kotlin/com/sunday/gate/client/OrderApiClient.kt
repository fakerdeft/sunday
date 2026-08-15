package com.sunday.gate.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

@Component
class OrderApiClient(
    @Value("\${clients.order-api.url}") orderApiUrl: String
) {
    private val restClient = RestClient.builder()
        .baseUrl(orderApiUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(1))
            setReadTimeout(Duration.ofSeconds(2))
        })
        .build()

    /**
     * 재고 현황을 조회한다. 입장 스케줄러가 주기적으로 한 번씩만 호출하므로
     * 대기 인원이 늘어도 주문 서버로 가는 호출 수는 늘지 않는다.
     */
    fun getStockSnapshot(productId: Long): ProductStockSnapshot =
        restClient.get()
            .uri("/api/orders/products/{productId}/stock-snapshot", productId)
            .retrieve()
            .body(ProductStockSnapshot::class.java)
            ?: throw IllegalStateException("상품 $productId 의 재고 현황을 가져오지 못했습니다.")
}
