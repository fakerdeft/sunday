package com.sunday.payment.repository.outbox

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
