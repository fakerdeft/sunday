package com.sunday.order.application

enum class OrderQueueStatus {
    WAITING,
    PROCESSING,
    SUCCEEDED,
    SOLD_OUT,
    REJECTED,
    FAILED;

    fun isTerminal(): Boolean =
        this == SUCCEEDED || this == SOLD_OUT || this == REJECTED || this == FAILED
}
