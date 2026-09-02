package com.sunday.order.application

import com.sunday.order.domain.AlreadyPurchasedException
import com.sunday.order.domain.DuplicatePendingOrderException
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

/** 운영 주문 경로. 비교 측정 경로는 `com.sunday.order.benchmark` 에 따로 있다. */
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

        /** 게이트는 토큰 하나에 재고 하나를 차감한다. 수량을 늘리면 게이트 집계와 어긋난다. */
        const val ORDER_QUANTITY = 1
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

    /** 게이트 스케줄러 전용. 주기당 상품 하나에 한 번만 호출된다. */
    @Transactional(readOnly = true)
    fun getStockSnapshot(productId: Long): ProductStockSnapshot = ProductStockSnapshot(
        productId = productId,
        availableStock = productStockRepository.countAvailable(productId)
    )

    /**
     * 토큰 해시를 예약 키로 써서 토큰 하나가 예약 하나만 만들게 한다.
     *
     * 트랜잭션을 걸지 않는다. 유니크 제약 위반 후 같은 트랜잭션에서는 복구 조회가 불가능하다.
     */
    fun createAdmittedReservation(
        memberId: Long,
        productId: Long,
        tokenFingerprint: String
    ): OrderReservation {
        val reservationKey = ReservationOrigin.ADMITTED.key(tokenFingerprint)

        reservationRepository.findByReservationKey(reservationKey)?.let { return it }

        return try {
            stockReservationService.reserve(
                memberId = memberId,
                productId = productId,
                quantity = ORDER_QUANTITY,
                reservationKey = reservationKey
            )
        } catch (e: DuplicatePendingOrderException) {
            // 같은 토큰의 동시 요청이면 기존 예약, 다른 토큰이면 조회 결과가 없어 그대로 전파
            reservationRepository.findByReservationKey(reservationKey) ?: throw e
        }
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

        // 비관적 락 기준선 예약만 수량 컬럼 복구
        val product = productRepository.findByIdWithPessimisticLock(reservation.productId)
            ?: throw ProductNotFoundException(reservation.productId)

        product.increaseStock(reservation.quantity)
        productRepository.save(product)
    }
}
