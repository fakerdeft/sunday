package com.sunday.order.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductStockJpaRepository : JpaRepository<ProductStockJpaEntity, Long> {

    @Query(
        value = "SELECT * FROM sunday.product_stock WHERE product_id = :productId AND status = 'AVAILABLE' LIMIT 1 FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    fun findOneAvailableWithSkipLocked(productId: Long): ProductStockJpaEntity?

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT s FROM ProductStockJpaEntity s WHERE s.productId = :productId AND s.status = com.sunday.order.domain.StockStatus.AVAILABLE")
    fun findAvailableByProductId(productId: Long): List<ProductStockJpaEntity>

    fun countByProductIdAndStatus(productId: Long, status: com.sunday.order.domain.StockStatus): Long

    fun deleteByProductId(productId: Long)

    @Modifying
    @Query(
        value = "UPDATE sunday.product_stock SET status = 'AVAILABLE', reserved_by = NULL WHERE product_id = :productId AND reserved_by = :memberId AND status = 'SOLD'",
        nativeQuery = true
    )
    fun releaseByMemberId(productId: Long, memberId: Long): Int
}
