package com.sunday.order.application

import com.sunday.order.domain.AlreadyPurchasedException
import com.sunday.order.domain.DuplicatePendingOrderException
import com.sunday.order.domain.HotDealNotActiveException
import com.sunday.order.domain.InvalidOrderQuantityException
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.OutOfStockException
import com.sunday.order.domain.Product
import com.sunday.order.domain.ProductNotFoundException
import com.sunday.order.domain.StockReservationMismatchException
import com.sunday.order.repository.OrderRepository
import com.sunday.order.repository.OrderReservationRepository
import com.sunday.order.repository.ProductRepository
import com.sunday.order.repository.ProductStockRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * `product_stock` 단위 재고 행을 `FOR UPDATE SKIP LOCKED`로 선점하는 공통 로직이다.
 *
 * 운영 주문 경로와 비교 측정 경로가 같은 선점 방식을 쓰도록 여기에 모아 두었다.
 * 경로마다 다른 것은 예약 키뿐이며, 예약 키는 [com.sunday.order.domain.ReservationOrigin] 이 만든다.
 */
@Service
class StockReservationService(
    private val productRepository: ProductRepository,
    private val reservationRepository: OrderReservationRepository,
    private val orderRepository: OrderRepository,
    private val productStockRepository: ProductStockRepository
) {
    @Transactional
    fun reserve(
        memberId: Long,
        productId: Long,
        quantity: Int,
        reservationKey: String
    ): OrderReservation {
        validateQuantity(quantity)
        checkDuplicate(memberId, productId)

        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)

        validateProductForReservation(product, memberId)

        val reservation = savePendingReservation(
            OrderReservation.create(
                memberId = memberId,
                product = product,
                quantity = quantity,
                reservationKey = reservationKey
            )
        )

        repeat(quantity) { claimed ->
            productStockRepository.claimWithSkipLocked(productId, memberId, reservation.id)
                ?: throw OutOfStockException(productId, quantity, claimed)
        }

        return reservation
    }

    fun release(reservation: OrderReservation) {
        val released = productStockRepository.releaseByReservationId(reservation.id)

        if (released != reservation.quantity) {
            throw StockReservationMismatchException(reservation.id, reservation.quantity, released)
        }
    }

    fun validateQuantity(quantity: Int) {
        if (quantity <= 0) {
            throw InvalidOrderQuantityException(quantity)
        }
    }

    fun checkDuplicate(memberId: Long, productId: Long) {
        if (reservationRepository.existsPendingReservation(memberId, productId)) {
            throw DuplicatePendingOrderException(memberId, productId)
        }
    }

    fun validateProductForReservation(product: Product, memberId: Long) {
        if (product.isHotDeal && !product.isHotDealActive()) {
            throw HotDealNotActiveException(product.id)
        }
        if (orderRepository.existsPaidOrder(memberId, product.id)) {
            throw AlreadyPurchasedException(memberId, product.id)
        }
    }

    fun savePendingReservation(reservation: OrderReservation): OrderReservation =
        try {
            reservationRepository.saveAndFlush(reservation)
        } catch (e: DataIntegrityViolationException) {
            throw DuplicatePendingOrderException(reservation.memberId, reservation.productId)
        }
}
