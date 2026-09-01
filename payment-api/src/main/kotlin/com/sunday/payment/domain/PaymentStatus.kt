package com.sunday.payment.domain

enum class PaymentStatus {
    PROCESSING,

    ACCOUNT_DEBITED,

    ORDER_CONFIRMED,

    COMPLETED,

    FAILED,

    REFUND_PROCESSING,

    REFUNDED
}
