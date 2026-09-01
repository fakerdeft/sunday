package com.sunday.order.domain

import java.util.UUID

/**
 * 예약 키 접두어. 취소·만료 시 재고 복구 방식을 가르는 기준이다.
 * 접두어가 흩어지면 새 경로 추가 시 복구 분기를 빠뜨리므로 여기에서만 정의한다.
 */
enum class ReservationOrigin(private val prefix: String) {
    /** 운영 경로 */
    ADMITTED("admitted"),

    STREAM_QUEUE("queue"),

    SKIP_LOCKED("skip-locked"),

    /** 비교 측정 전용. 수량 컬럼 복구 대상 */
    PESSIMISTIC("pessimistic");

    fun newKey(): String = key(UUID.randomUUID().toString())

    fun key(id: String): String = "$prefix:$id"

    fun matches(reservationKey: String): Boolean = reservationKey.startsWith("$prefix:")

    fun usesUnitStock(): Boolean = this != PESSIMISTIC

    companion object {
        fun of(reservationKey: String): ReservationOrigin? =
            entries.firstOrNull { origin -> origin.matches(reservationKey) }
    }
}
