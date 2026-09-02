package com.sunday.gate.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "sunday.order-pass")
data class OrderPassProperties(
    var keyPrefix: String = "sunday:pass",

    /** 토큰 유효 시간. 해제 요청이 오지 않는 이탈 건의 회수 시간이다. */
    var passTtl: Duration = Duration.ofSeconds(30),

    /** budget TTL. 동기화가 끊기면 만료돼 통과가 막힌다. 낡은 수량으로 발급하지 않기 위한 안전장치. */
    var budgetTtl: Duration = Duration.ofSeconds(10),

    var managedProductIds: List<Long> = listOf(1L)
) {
    fun budgetKey(productId: Long): String = "$keyPrefix:{$productId}:budget"

    fun inFlightKey(productId: Long): String = "$keyPrefix:{$productId}:in-flight"

    fun keyTtlSeconds(): Long = maxOf(budgetTtl.seconds, passTtl.seconds)
}
