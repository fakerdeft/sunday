package com.sunday.payment.adapter.outbound

import com.sunday.order.port.inbound.OrderUseCase
import com.sunday.payment.port.outbound.OrderInfo
import com.sunday.payment.port.outbound.OrderPort
import org.springframework.stereotype.Component

@Component
class OrderPortAdapter(
    private val orderUseCase: OrderUseCase
) : OrderPort {

    override fun getOrderInfo(orderId: Long): OrderInfo {
        val order = orderUseCase.getOrder(orderId)

        return OrderInfo(
            orderId = order.id,
            memberId = order.memberId,
            totalAmount = order.totalAmount,
            status = order.status.name,
            isExpired = order.isExpired()
        )
    }

    override fun markOrderAsPaid(orderId: Long) {
        orderUseCase.markOrderAsPaid(orderId)
    }

    override fun cancelOrder(orderId: Long) {
        orderUseCase.cancelOrder(orderId)
    }
}
