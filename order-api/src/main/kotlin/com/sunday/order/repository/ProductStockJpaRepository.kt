package com.sunday.order.repository

import com.sunday.order.domain.StockStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ProductStockJpaRepository : JpaRepository<ProductStockJpaEntity, Long> {
    fun countByProductIdAndStatus(productId: Long, status: StockStatus): Long

    fun countByReservationIdAndStatus(
        reservationId: Long,
        status: StockStatus
    ): Long

    fun deleteByProductId(productId: Long)
}
