package com.sunday.order.port.outbound

import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderStatus

/**
 * Order Repository (Output Port)
 */
interface OrderRepository {
    fun findById(id: Long): Order?
    fun findByMemberId(memberId: Long): List<Order>
    fun findByStatus(status: OrderStatus): List<Order>
    fun findExpiredPendingOrders(): List<Order>
    fun existsPendingOrder(memberId: Long, productId: Long): Boolean
    fun save(order: Order): Order
    fun deleteAll()
}
