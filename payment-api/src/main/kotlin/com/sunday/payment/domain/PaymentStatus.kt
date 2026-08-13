package com.sunday.payment.domain

enum class PaymentStatus {
    /** 결제 처리 중 */
    PROCESSING,

    /** 계좌 차감 응답까지 확인된 상태 */
    ACCOUNT_DEBITED,

    /** 주문 확정 응답까지 확인된 상태 */
    ORDER_CONFIRMED,

    /** 결제 완료 */
    COMPLETED,

    /** 결제 실패 */
    FAILED,

    /** 환불 단계가 시작되어 재시도 가능한 상태 */
    REFUND_PROCESSING,

    /** 환불됨 */
    REFUNDED
}
