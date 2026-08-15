package com.sunday.order.application

import com.sunday.order.domain.AlreadyPurchasedException
import com.sunday.order.domain.InvalidOrderStatusException
import com.sunday.order.domain.Order
import com.sunday.order.domain.OrderNotFoundException
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.OrderStatus
import com.sunday.order.domain.Product
import com.sunday.order.domain.ProductNotFoundException
import com.sunday.order.domain.ReservationExpiredException
import com.sunday.order.domain.ReservationNotFoundException
import com.sunday.order.domain.ReservationOrigin
import com.sunday.order.domain.ReservationStatus
import com.sunday.order.repository.OrderRepository
import com.sunday.order.repository.OrderReservationRepository
import com.sunday.order.repository.ProductRepository
import com.sunday.order.repository.ProductStockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운영 주문 경로다.
 *
 * 주문 생성은 대기열에서 입장이 허가된 회원만 [createAdmittedReservation] 으로 호출한다.
 * 재고 선점 방식을 비교하기 위한 경로는 `com.sunday.order.benchmark` 패키지에 따로 있다.
 */
@Service
class OrderService(
    private val productRepository: ProductRepository,
    private val reservationRepository: OrderReservationRepository,
    private val orderRepository: OrderRepository,
    private val productStockRepository: ProductStockRepository,
    private val stockReservationService: StockReservationService
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

    /**
     * 게이트 서버가 발급 가능 수량을 정하는 데 쓰는 재고 현황이다.
     * 주기마다 상품당 한 번만 호출되므로 대기 트래픽이 늘어도 이 조회는 늘지 않는다.
     */
    @Transactional(readOnly = true)
    fun getStockSnapshot(productId: Long): ProductStockSnapshot = ProductStockSnapshot(
        productId = productId,
        availableStock = productStockRepository.countAvailable(productId)
    )

    /**
     * 대기열에서 입장이 허가된 회원의 주문이다. 입장 인원이 제한되어 있으므로 대기 없이 바로 처리한다.
     */
    @Transactional
    fun createAdmittedReservation(memberId: Long, productId: Long, quantity: Int): OrderReservation =
        stockReservationService.reserve(
            memberId = memberId,
            productId = productId,
            quantity = quantity,
            reservationKey = ReservationOrigin.ADMITTED.newKey()
        )

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

    private fun withAvailability(products: List<Product>): List<ProductAvailability> {
        if (products.isEmpty()) {

            return emptyList()
        }

        val availableByProductId = productStockRepository.countAvailableByProductIds(products.map { it.id })

        return products.map { product ->
            ProductAvailability(product, availableByProductId[product.id] ?: 0L)
        }
    }

    private fun restoreStock(reservation: OrderReservation) {
        val origin = ReservationOrigin.of(reservation.reservationKey)

        if (origin == null || origin.usesUnitStock()) {
            stockReservationService.release(reservation)

            return
        }

        // 비교 측정 전용(비관적 락) 예약만 단일 수량 컬럼을 되돌린다.
        val product = productRepository.findByIdWithPessimisticLock(reservation.productId)
            ?: throw ProductNotFoundException(reservation.productId)

        product.increaseStock(reservation.quantity)
        productRepository.save(product)
    }
}
