package com.sunday.order.adapter.inbound.dto

data class AsyncOrderResponse(
    val status: String = "PROCESSING",
    val reservationKey: String,
    val message: String = "주문이 접수되었습니다. 잠시 후 주문 내역을 확인해주세요."
)
