package com.sunday.order.domain

import java.util.UUID

/**
 * 예약이 어느 경로에서 만들어졌는지 나타낸다.
 *
 * 예약 키의 접두어로 저장되며, 취소·만료 시 어떤 방식으로 재고를 되돌릴지 판단하는 기준이 된다.
 * 접두어 문자열이 여러 곳에 흩어지면 새 경로를 추가할 때 복구 분기를 빠뜨리기 쉬우므로 여기에서만 정의한다.
 */
enum class ReservationOrigin(private val prefix: String) {
    /** 운영 경로. 대기열에서 입장이 허가된 회원의 주문 */
    ADMITTED("admitted"),

    /** 비교 측정 전용. Redis Streams 접수 큐 워커가 만든 예약 */
    STREAM_QUEUE("queue"),

    /** 비교 측정 전용. `FOR UPDATE SKIP LOCKED` 직접 호출 기준선 */
    SKIP_LOCKED("skip-locked"),

    /** 비교 측정 전용. 단일 수량 컬럼 + 비관적 락 기준선 */
    PESSIMISTIC("pessimistic");

    fun newKey(): String = key(UUID.randomUUID().toString())

    fun key(id: String): String = "$prefix:$id"

    fun matches(reservationKey: String): Boolean = reservationKey.startsWith("$prefix:")

    /** 수량 컬럼이 아닌 `product_stock` 단위 재고 행으로 재고를 관리하는지 여부 */
    fun usesUnitStock(): Boolean = this != PESSIMISTIC

    companion object {
        fun of(reservationKey: String): ReservationOrigin? =
            entries.firstOrNull { origin -> origin.matches(reservationKey) }
    }
}
