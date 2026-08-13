package com.sunday.order.application

data class LoadTestState(
    val productId: Long,
    val pendingReservations: Long,
    val availableUnitStocks: Long,
    val productStockColumn: Int
)
