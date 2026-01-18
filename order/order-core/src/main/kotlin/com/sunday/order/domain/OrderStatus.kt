package com.sunday.order.domain

/**
 * 주문 상태
 */
enum class OrderStatus {
    /** 재고 선점 완료, 결제 대기 중 */
    PENDING,

    /** 결제 완료 */
    PAID,

    /** 주문 취소 (사용자 취소 또는 결제 실패) */
    CANCELLED,

    /** 결제 시간 초과로 자동 취소 */
    EXPIRED
}
