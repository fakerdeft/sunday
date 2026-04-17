package com.sunday.order.repository

import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.ReservationStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "order_reservations",
    schema = "sunday",
    indexes = [
        Index(name = "idx_reservations_member_id", columnList = "member_id"),
        Index(name = "idx_reservations_status_expire", columnList = "status, expire_at")
    ]
)
class OrderReservationJpaEntity(
    @Id
    @Tsid
    @Column(name = "id")
    val id: Long = 0L,

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
    var status: ReservationStatus,

    @Column(name = "reservation_key", nullable = false)
    val reservationKey: String,

    @Column(name = "expire_at", nullable = false)
    val expireAt: LocalDateTime,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun from(domain: OrderReservation): OrderReservationJpaEntity = OrderReservationJpaEntity(
            id = domain.id,
            memberId = domain.memberId,
            productId = domain.productId,
            productName = domain.productName,
            quantity = domain.quantity,
            unitPrice = domain.unitPrice,
            totalAmount = domain.totalAmount,
            status = domain.status,
            reservationKey = domain.reservationKey,
            expireAt = domain.expireAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
    
    fun toDomain(): OrderReservation = OrderReservation(
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
