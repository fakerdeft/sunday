package com.sunday.order.benchmark

data class BenchmarkState(
    val productId: Long,
    val pendingReservations: Long,
    val availableUnitStocks: Long,
    val productStockColumn: Int
)
