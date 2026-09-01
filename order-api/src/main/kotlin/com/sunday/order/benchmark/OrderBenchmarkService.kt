package com.sunday.order.benchmark

import com.sunday.order.application.StockReservationService
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.ProductNotFoundException
import com.sunday.order.domain.ProductStock
import com.sunday.order.domain.ReservationOrigin
import com.sunday.order.domain.ReservationStatus
import com.sunday.order.domain.StockStatus
import com.sunday.order.repository.OrderRepository
import com.sunday.order.repository.OrderReservationRepository
import com.sunday.order.repository.ProductRepository
import com.sunday.order.repository.ProductStockRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 재고 선점 방식 비교용 기준선. 운영 경로에서는 사용하지 않는다. */
@Profile("local")
@Service
class OrderBenchmarkService(
    private val productRepository: ProductRepository,
    private val reservationRepository: OrderReservationRepository,
    private val orderRepository: OrderRepository,
    private val productStockRepository: ProductStockRepository,
    private val stockReservationService: StockReservationService
) {
    @Transactional
    fun createWithSkipLocked(memberId: Long, productId: Long, quantity: Int): OrderReservation =
        stockReservationService.reserve(
            memberId = memberId,
            productId = productId,
            quantity = quantity,
            reservationKey = ReservationOrigin.SKIP_LOCKED.newKey()
        )

    @Transactional
    fun createWithPessimisticLock(memberId: Long, productId: Long, quantity: Int): OrderReservation {
        stockReservationService.validateQuantity(quantity)
        stockReservationService.checkDuplicate(memberId, productId)

        val product = productRepository.findByIdWithPessimisticLock(productId)
            ?: throw ProductNotFoundException(productId)

        stockReservationService.validateProductForReservation(product, memberId)
        product.decreaseStock(quantity)
        productRepository.save(product)

        return stockReservationService.savePendingReservation(
            OrderReservation.create(
                memberId = memberId,
                product = product,
                quantity = quantity,
                reservationKey = ReservationOrigin.PESSIMISTIC.newKey()
            )
        )
    }

    @Transactional
    fun prepareProduct(productId: Long, quantity: Int) {
        orderRepository.deleteByProductId(productId)
        reservationRepository.deleteByProductId(productId)

        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)

        productRepository.save(
            product.copy(
                stock = quantity,
                totalQuantity = quantity,
                hotDealStartTime = LocalDateTime.now().minusMinutes(1),
                hotDealEndTime = LocalDateTime.now().plusHours(24)
            )
        )

        resetStock(productId, quantity)
    }

    @Transactional(readOnly = true)
    fun getState(productId: Long): BenchmarkState {
        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)

        return BenchmarkState(
            productId = productId,
            pendingReservations = reservationRepository.countByProductIdAndStatus(
                productId,
                ReservationStatus.PENDING
            ),
            availableUnitStocks = productStockRepository.countAvailable(productId),
            productStockColumn = product.stock
        )
    }

    private fun resetStock(productId: Long, quantity: Int) {
        productStockRepository.deleteByProductId(productId)
        val stocks = (1..quantity).map {
            ProductStock(
                id = 0L,
                productId = productId,
                status = StockStatus.AVAILABLE,
                version = 0L,
                reservedBy = null,
                createdAt = LocalDateTime.now()
            )
        }

        productStockRepository.saveAll(stocks)
    }
}
