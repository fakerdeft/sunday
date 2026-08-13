package com.sunday.order.repository

import com.sunday.order.domain.ProductStock
import com.sunday.order.domain.StockStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(
    name = "product_stock",
    schema = "order_service",
    indexes = [Index(name = "idx_product_stock_available", columnList = "product_id, status")]
)
class ProductStockJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: StockStatus = StockStatus.AVAILABLE,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L,

    @Column(name = "reserved_by")
    var reservedBy: Long? = null,

    @Column(name = "reservation_id")
    var reservationId: Long? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun from(domain: ProductStock): ProductStockJpaEntity = ProductStockJpaEntity(
            id = domain.id,
            productId = domain.productId,
            status = domain.status,
            version = domain.version,
            reservedBy = domain.reservedBy,
            reservationId = domain.reservationId,
            createdAt = domain.createdAt
        )
    }

    fun toDomain(): ProductStock = ProductStock(
        id = id,
        productId = productId,
        status = status,
        version = version,
        reservedBy = reservedBy,
        reservationId = reservationId,
        createdAt = createdAt
    )
}
