package com.sunday.gate.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "sunday.order-pass")
data class OrderPassProperties(
    var keyPrefix: String = "sunday:pass",

    /**
     * 통행증 유효 시간.
     *
     * 통과한 회원은 주문을 마치면 통행증을 반납한다. 창을 닫거나 이탈해 반납이 오지 않으면
     * 이 시간이 지나 회수되고, 다음 동기화 때 발급 가능 수량으로 돌아온다.
     */
    var passTtl: Duration = Duration.ofSeconds(30),

    /**
     * 발급 가능 수량의 TTL.
     *
     * 주문 서버가 멈춰 동기화가 끊기면 이 시간 뒤에 수량이 사라져 통과가 막힌다.
     * 낡은 수량으로 계속 통행증을 내주지 않기 위한 안전장치다.
     */
    var budgetTtl: Duration = Duration.ofSeconds(10),

    /** 통행증을 발급할 상품. 스케줄러가 이 상품들의 재고만 동기화한다. */
    var managedProductIds: List<Long> = listOf(1L)
) {
    fun budgetKey(productId: Long): String = "$keyPrefix:{$productId}:budget"

    fun inFlightKey(productId: Long): String = "$keyPrefix:{$productId}:in-flight"

    /** 통행증 집합은 발급된 통행증이 만료될 때까지는 살아 있어야 한다. */
    fun keyTtlSeconds(): Long = maxOf(budgetTtl.seconds, passTtl.seconds)
}
