package com.sunday.order.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sunday.order.domain.ProductStock
import com.sunday.order.domain.StockStatus
import jakarta.persistence.LockModeType
import org.hibernate.Timeouts
import org.hibernate.jpa.SpecHints
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ProductStockRepository(
    private val jpaRepository: ProductStockJpaRepository,
    private val queryDsl: JPAQueryFactory
) {
    private val stock = QProductStockJpaEntity.productStockJpaEntity

    fun claimWithSkipLocked(productId: Long, memberId: Long, reservationId: Long): ProductStock? {
        val entity = queryDsl.selectFrom(stock)
            .where(
                stock.productId.eq(productId),
                stock.status.eq(StockStatus.AVAILABLE)
            )
            .orderBy(stock.id.asc())
            .limit(1)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setHint(SpecHints.HINT_SPEC_LOCK_TIMEOUT, Timeouts.SKIP_LOCKED_MILLI)
            .fetchFirst()
            ?: return null

        entity.status = StockStatus.SOLD
        entity.reservedBy = memberId
        entity.reservationId = reservationId

        return entity.toDomain()
    }

    fun countAvailable(productId: Long): Long =
        jpaRepository.countByProductIdAndStatus(productId, StockStatus.AVAILABLE)

    fun countAvailableByProductIds(productIds: Collection<Long>): Map<Long, Long> {
        if (productIds.isEmpty()) {
            return emptyMap()
        }

        val availableStock = stock.count()
        val counts = queryDsl
            .select(stock.productId, availableStock)
            .from(stock)
            .where(
                stock.productId.`in`(productIds),
                stock.status.eq(StockStatus.AVAILABLE)
            )
            .groupBy(stock.productId)
            .fetch()

        return counts.associate { count ->
            count.get(stock.productId)!! to count.get(availableStock)!!
        }
    }

    fun saveAll(stocks: List<ProductStock>) =
        jpaRepository.saveAll(stocks.map { ProductStockJpaEntity.from(it) })

    fun deleteByProductId(productId: Long) =
        jpaRepository.deleteByProductId(productId)

    fun countClaimedByReservationId(reservationId: Long): Long =
        jpaRepository.countByReservationIdAndStatus(reservationId, StockStatus.SOLD)

    fun releaseByReservationId(reservationId: Long): Int {
        val updatedCount = queryDsl.update(stock)
            .set(stock.status, StockStatus.AVAILABLE)
            .setNull(stock.reservedBy)
            .setNull(stock.reservationId)
            .where(
                stock.reservationId.eq(reservationId),
                stock.status.eq(StockStatus.SOLD)
            )
            .execute()

        return updatedCount.toInt()
    }

    fun findById(id: Long): ProductStock? =
        jpaRepository.findByIdOrNull(id)?.toDomain()
}
