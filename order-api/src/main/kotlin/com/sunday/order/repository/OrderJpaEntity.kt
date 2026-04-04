package com.sunday.order.repository

import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "orders",
    schema = "sunday",
    indexes = [
        Index(name = "idx_orders_member_id", columnList = "member_id"),
        Index(name = "idx_orders_status_expire_at", columnList = "status, expire_at")
    ]
)
class OrderJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, insertable = false, updatable = false)
    val product: ProductJpaEntity? = null,

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
    var status: OrderStatus,

    @Column(name = "reservation_key", nullable = false)
    val reservationKey: String,

    @Column(name = "expire_at", nullable = false)
    val expireAt: LocalDateTime,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun updateFrom(order: Order) {
        this.status = order.status
        this.updatedAt = order.updatedAt
    }
}
