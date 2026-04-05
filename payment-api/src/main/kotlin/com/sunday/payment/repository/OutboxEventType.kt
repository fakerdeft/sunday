package com.sunday.payment.repository

enum class OutboxEventType {
    PAYMENT_COMPLETED,
    PAYMENT_REFUNDED
}
