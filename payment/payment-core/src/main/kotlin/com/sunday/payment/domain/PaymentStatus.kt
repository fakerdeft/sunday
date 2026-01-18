package com.sunday.payment.domain

enum class PaymentStatus {
    /** 결제 처리 중 */
    PROCESSING,

    /** 결제 완료 */
    COMPLETED,

    /** 결제 실패 */
    FAILED,

    /** 환불됨 */
    REFUNDED
}
