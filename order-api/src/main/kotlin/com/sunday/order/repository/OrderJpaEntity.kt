package com.sunday.order.repository

import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "orders",
    schema = "order_service",
    indexes = [Index(name = "idx_orders_member_id", columnList = "member_id")]
)
class OrderJpaEntity(
    @Id
    @Column(name = "reservation_id")
    val reservationId: Long,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "product_name", nullable = false)
    val productName: String,

    @Column(name = "quantity", nullable = false)
    val quantity: Int,

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    val unitPrice: BigDecimal,

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    val totalAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OrderStatus = OrderStatus.PAID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun from(domain: Order): OrderJpaEntity = OrderJpaEntity(
            reservationId = domain.reservationId,
            memberId = domain.memberId,
            productId = domain.productId,
            productName = domain.productName,
            quantity = domain.quantity,
            unitPrice = domain.unitPrice,
            totalAmount = domain.totalAmount,
            status = domain.status,
            createdAt = domain.createdAt
        )
    }

    fun toDomain(): Order = Order(
        reservationId = reservationId,
        memberId = memberId,
        productId = productId,
        productName = productName,
        quantity = quantity,
        unitPrice = unitPrice,
        totalAmount = totalAmount,
        status = status,
        createdAt = createdAt
    )
}
