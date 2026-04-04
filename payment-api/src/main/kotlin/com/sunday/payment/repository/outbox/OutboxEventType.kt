package com.sunday.payment.repository.outbox

enum class OutboxEventType {
    PAYMENT_COMPLETED,
    PAYMENT_REFUNDED
}
