package com.sunday.payment.repository

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
