package com.sunday.order.repository

import com.sunday.order.domain.ProductStock
import com.sunday.order.domain.StockStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ProductStockRepository(private val jpaRepository: ProductStockJpaRepository) {

    fun claimWithSkipLocked(productId: Long, memberId: Long, reservationId: Long): ProductStock? {

        val entity = jpaRepository.findOneAvailableWithSkipLocked(productId) ?: return null

        entity.status = StockStatus.SOLD
        entity.reservedBy = memberId
        entity.reservationId = reservationId

        return jpaRepository.save(entity).toDomain()
    }

    fun countAvailable(productId: Long): Long =
        jpaRepository.countByProductIdAndStatus(productId, StockStatus.AVAILABLE)

    fun countAvailableByProductIds(productIds: Collection<Long>): Map<Long, Long> =
        jpaRepository.countAvailableByProductIds(productIds)
            .associate { it.productId to it.availableStock }

    fun saveAll(stocks: List<ProductStock>) =
        jpaRepository.saveAll(stocks.map { ProductStockJpaEntity.from(it) })

    fun deleteByProductId(productId: Long) =
        jpaRepository.deleteByProductId(productId)

    fun countClaimedByReservationId(reservationId: Long): Long =
        jpaRepository.countByReservationIdAndStatus(reservationId, StockStatus.SOLD)

    fun releaseByReservationId(reservationId: Long): Int =
        jpaRepository.releaseByReservationId(reservationId)

    fun findById(id: Long): ProductStock? =
        jpaRepository.findByIdOrNull(id)?.toDomain()
}
