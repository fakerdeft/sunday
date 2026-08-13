package com.sunday.order.application

import com.sunday.order.domain.AlreadyPurchasedException
import com.sunday.order.domain.DuplicatePendingOrderException
import com.sunday.order.domain.HotDealNotActiveException
import com.sunday.order.domain.InvalidOrderQuantityException
import com.sunday.order.domain.InvalidOrderStatusException
import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderNotFoundException
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.OrderStatus
import com.sunday.order.domain.OutOfStockException
import com.sunday.order.domain.Product
import com.sunday.order.domain.ProductNotFoundException
import com.sunday.order.domain.ReservationExpiredException
import com.sunday.order.domain.ReservationNotFoundException
import com.sunday.order.domain.ReservationStatus
import com.sunday.order.domain.StockReservationMismatchException
import com.sunday.order.repository.OrderRepository
import com.sunday.order.repository.OrderReservationRepository
import com.sunday.order.repository.ProductRepository
import com.sunday.order.repository.ProductStockRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrderService(
    private val productRepository: ProductRepository,
    private val reservationRepository: OrderReservationRepository,
    private val orderRepository: OrderRepository,
    private val productStockRepository: ProductStockRepository
) {
    companion object {
        private const val EXPIRATION_BATCH_SIZE = 100
    }

    @Transactional(readOnly = true)
    fun getProducts(): List<ProductAvailability> = withAvailability(productRepository.findAll())

    @Transactional(readOnly = true)
    fun getHotDeals(): List<ProductAvailability> = withAvailability(productRepository.findHotDeals())

    @Transactional(readOnly = true)
    fun getProduct(productId: Long): ProductAvailability {
        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)

        return ProductAvailability(product, productStockRepository.countAvailable(productId))
    }

    @Transactional
    fun createQueuedReservation(
        requestId: String,
        memberId: Long,
        productId: Long,
        quantity: Int
    ): OrderReservation {
        val reservationKey = "queue:$requestId"
        val existingReservation = reservationRepository.findByReservationKey(reservationKey)

        if (existingReservation != null) {

            return existingReservation
        }

        return reserveUnitStock(memberId, productId, quantity, reservationKey)
    }

    @Transactional(readOnly = true)
    fun getMyReservations(memberId: Long): List<OrderReservation> =
        reservationRepository.findByMemberId(memberId)

    @Transactional(readOnly = true)
    fun getReservation(reservationId: Long): OrderReservation =
        reservationRepository.findById(reservationId) ?: throw ReservationNotFoundException(reservationId)

    @Transactional
    fun cancelReservation(reservationId: Long): OrderReservation {
        val reservation = reservationRepository.findByIdForUpdate(reservationId)
            ?: throw ReservationNotFoundException(reservationId)

        if (reservation.status == ReservationStatus.CANCELLED) {

            return reservation
        }

        if (reservation.status != ReservationStatus.PENDING) {
            throw InvalidOrderStatusException(reservationId, reservation.status.name, "PENDING")
        }

        val cancelled = reservation.cancel()

        restoreStock(cancelled)

        return reservationRepository.save(cancelled)
    }

    @Transactional
    fun confirmReservation(reservationId: Long): Order {
        val reservation = reservationRepository.findByIdForUpdate(reservationId)
            ?: throw ReservationNotFoundException(reservationId)

        if (reservation.status == ReservationStatus.CONFIRMED) {

            return orderRepository.findByReservationId(reservationId)
                ?: throw InvalidOrderStatusException(
                    reservationId,
                    reservation.status.name,
                    "CONFIRMED_WITH_ORDER"
                )
        }
        if (reservation.status != ReservationStatus.PENDING) {
            throw InvalidOrderStatusException(reservationId, reservation.status.name, "PENDING")
        }
        if (reservation.isExpired()) {
            throw ReservationExpiredException(reservationId)
        }
        if (orderRepository.existsPaidOrder(reservation.memberId, reservation.productId)) {
            throw AlreadyPurchasedException(reservation.memberId, reservation.productId)
        }

        val confirmed = reservation.confirm()

        reservationRepository.save(confirmed)

        return orderRepository.save(Order.from(confirmed))
    }

    @Transactional(readOnly = true)
    fun getMyOrders(memberId: Long): List<Order> = orderRepository.findByMemberId(memberId)

    @Transactional(readOnly = true)
    fun getOrder(reservationId: Long): Order =
        orderRepository.findByReservationId(reservationId) ?: throw OrderNotFoundException(reservationId)

    @Transactional
    fun cancelOrder(reservationId: Long) {
        val order = orderRepository.findByReservationIdForUpdate(reservationId)
            ?: throw OrderNotFoundException(reservationId)

        if (order.status != OrderStatus.CANCELLED) {
            orderRepository.save(order.cancel())
        }
    }

    @Transactional
    fun expireReservations(): Int {
        val expired = reservationRepository.findExpiredPendingReservationsForUpdate(EXPIRATION_BATCH_SIZE)

        expired.forEach { reservation ->
            restoreStock(reservation)
            reservationRepository.save(reservation.expire())
        }

        return expired.size
    }

    /**
     * 로컬 부하 테스트에서 실제 주문 처리 방식과 비관적 락 방식을 비교하기 위한 직접 호출 경로다.
     */
    @Transactional
    fun createReservationWithSkipLocked(memberId: Long, productId: Long, quantity: Int): OrderReservation {
        return reserveUnitStock(memberId, productId, quantity, "skip-locked:${UUID.randomUUID()}")
    }

    /**
     * 로컬 부하 테스트에서 사용하는 DB 비관적 락 기준선이다.
     */
    @Transactional
    fun createReservationWithPessimisticLock(
        memberId: Long,
        productId: Long,
        quantity: Int
    ): OrderReservation {
        validateQuantity(quantity)
        checkDuplicate(memberId, productId)

        val product = productRepository.findByIdWithPessimisticLock(productId)
            ?: throw ProductNotFoundException(productId)

        validateProductForReservation(product, memberId)
        product.decreaseStock(quantity)
        productRepository.save(product)

        return savePendingReservation(
            OrderReservation.create(
                memberId = memberId,
                product = product,
                quantity = quantity,
                reservationKey = "pessimistic:${UUID.randomUUID()}"
            )
        )
    }

    private fun withAvailability(products: List<Product>): List<ProductAvailability> {
        if (products.isEmpty()) {

            return emptyList()
        }

        val availableByProductId = productStockRepository.countAvailableByProductIds(products.map { it.id })

        return products.map { product ->
            ProductAvailability(product, availableByProductId[product.id] ?: 0L)
        }
    }

    private fun reserveUnitStock(
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

    private fun validateQuantity(quantity: Int) {
        if (quantity <= 0) {
            throw InvalidOrderQuantityException(quantity)
        }
    }

    private fun checkDuplicate(memberId: Long, productId: Long) {
        if (reservationRepository.existsPendingReservation(memberId, productId)) {
            throw DuplicatePendingOrderException(memberId, productId)
        }
    }

    private fun validateProductForReservation(product: Product, memberId: Long) {
        if (product.isHotDeal && !product.isHotDealActive()) {
            throw HotDealNotActiveException(product.id)
        }
        if (orderRepository.existsPaidOrder(memberId, product.id)) {
            throw AlreadyPurchasedException(memberId, product.id)
        }
    }

    private fun savePendingReservation(reservation: OrderReservation): OrderReservation =
        try {
            reservationRepository.saveAndFlush(reservation)
        } catch (e: DataIntegrityViolationException) {
            throw DuplicatePendingOrderException(reservation.memberId, reservation.productId)
        }

    private fun restoreStock(reservation: OrderReservation) {
        if (
            reservation.reservationKey.startsWith("skip-locked:") ||
            reservation.reservationKey.startsWith("queue:")
        ) {
            val released = productStockRepository.releaseByReservationId(reservation.id)

            if (released != reservation.quantity) {
                throw StockReservationMismatchException(reservation.id, reservation.quantity, released)
            }

            return
        }

        val product = productRepository.findByIdWithPessimisticLock(reservation.productId)
            ?: throw ProductNotFoundException(reservation.productId)

        product.increaseStock(reservation.quantity)
        productRepository.save(product)
    }
}
