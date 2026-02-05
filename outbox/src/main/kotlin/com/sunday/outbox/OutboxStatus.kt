package com.sunday.outbox

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
