package com.sunday.order.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "product", schema = "sunday")
class ProductJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    val price: BigDecimal,

    @Column(name = "stock", nullable = false)
    var stock: Int,

    @Column(name = "total_quantity", nullable = false)
    val totalQuantity: Int,

    @Column(name = "is_hot_deal", nullable = false)
    val isHotDeal: Boolean = false,

    @Column(name = "hot_deal_start_time")
    val hotDealStartTime: LocalDateTime? = null,

    @Column(name = "hot_deal_end_time")
    val hotDealEndTime: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
