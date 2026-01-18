package com.sunday.order.adapter.outbound

import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderStatus
import jakarta.persistence.*
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
    fun toDomain(): Order {
        return Order(
            id = id,
            memberId = memberId,
            productId = productId,
            productName = productName,
            quantity = quantity,
            unitPrice = unitPrice,
            totalAmount = totalAmount,
            status = status,
            reservationKey = reservationKey,
            expireAt = expireAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun updateFrom(order: Order) {
        status = order.status
        updatedAt = order.updatedAt
    }

    companion object {
        fun fromDomain(order: Order): OrderJpaEntity {
            return OrderJpaEntity(
                id = order.id,
                memberId = order.memberId,
                productId = order.productId,
                productName = order.productName,
                quantity = order.quantity,
                unitPrice = order.unitPrice,
                totalAmount = order.totalAmount,
                status = order.status,
                reservationKey = order.reservationKey,
                expireAt = order.expireAt,
                createdAt = order.createdAt,
                updatedAt = order.updatedAt
            )
        }
    }
}
