package com.sunday.order.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductStockJpaRepository : JpaRepository<ProductStockJpaEntity, Long> {

    @Query(
        value = "SELECT * FROM order_service.product_stock WHERE product_id = :productId AND status = 'AVAILABLE' LIMIT 1 FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    fun findOneAvailableWithSkipLocked(productId: Long): ProductStockJpaEntity?

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT s FROM ProductStockJpaEntity s WHERE s.productId = :productId AND s.status = com.sunday.order.domain.StockStatus.AVAILABLE")
    fun findAvailableByProductId(productId: Long): List<ProductStockJpaEntity>

    fun countByProductIdAndStatus(productId: Long, status: com.sunday.order.domain.StockStatus): Long

    @Query(
        """
        SELECT s.productId AS productId, COUNT(s) AS availableStock
        FROM ProductStockJpaEntity s
        WHERE s.productId IN :productIds
          AND s.status = com.sunday.order.domain.StockStatus.AVAILABLE
        GROUP BY s.productId
        """
    )
    fun countAvailableByProductIds(productIds: Collection<Long>): List<AvailableStockCount>

    fun countByReservationIdAndStatus(
        reservationId: Long,
        status: com.sunday.order.domain.StockStatus
    ): Long

    fun deleteByProductId(productId: Long)

    @Modifying
    @Query(
        value = "UPDATE order_service.product_stock SET status = 'AVAILABLE', reserved_by = NULL, reservation_id = NULL WHERE reservation_id = :reservationId AND status = 'SOLD'",
        nativeQuery = true
    )
    fun releaseByReservationId(reservationId: Long): Int
}

interface AvailableStockCount {
    val productId: Long
    val availableStock: Long
}
