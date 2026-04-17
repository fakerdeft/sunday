package com.sunday.order.application

import com.sunday.order.domain.*
import com.sunday.order.repository.OrderRepository
import com.sunday.order.repository.OrderReservationRepository
import com.sunday.order.repository.ProductRepository
import com.sunday.order.repository.ProductStockRepository
import com.sunday.support.infra.lock.DistributedLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class OrderService(
    private val productRepository: ProductRepository,
    private val reservationRepository: OrderReservationRepository,
    private val orderRepository: OrderRepository,
    private val productStockRepository: ProductStockRepository,
    private val stockCasManager: StockCasManager,
    private val redisTokenQueueManager: RedisTokenQueueManager
) {
    @Autowired
    private lateinit var applicationContext: ApplicationContext
    private val self: OrderService get() = applicationContext.getBean(OrderService::class.java)

    private val reentrantLocks = ConcurrentHashMap<Long, ReentrantLock>()

    // ========================
    // 상품 조회
    // ========================
    @Transactional(readOnly = true)
    fun getProducts(): List<Product> = productRepository.findAll()

    @Transactional(readOnly = true)
    fun getHotDeals(): List<Product> = productRepository.findHotDeals()

    @Transactional(readOnly = true)
    fun getProduct(productId: Long): Product =
        productRepository.findById(productId) ?: throw ProductNotFoundException(productId)

    // ========================
    // 선점 조회
    // ========================
    @Transactional(readOnly = true)
    fun getReservation(reservationId: Long): OrderReservation =
        reservationRepository.findById(reservationId) ?: throw ReservationNotFoundException(reservationId)

    @Transactional(readOnly = true)
    fun getMyReservations(memberId: Long): List<OrderReservation> =
        reservationRepository.findByMemberId(memberId)

    // ========================
    // 확정 주문 조회
    // ========================
    @Transactional(readOnly = true)
    fun getOrder(reservationId: Long): Order =
        orderRepository.findByReservationId(reservationId) ?: throw OrderNotFoundException(reservationId)

    @Transactional(readOnly = true)
    fun getMyOrders(memberId: Long): List<Order> = orderRepository.findByMemberId(memberId)

    // ========================
    // 1. ReentrantLock (JVM, 단일 인스턴스)
    // ========================
    fun createReservationWithReentrantLock(memberId: Long, productId: Long, quantity: Int): OrderReservation {
        val lock = reentrantLocks.getOrPut(productId) { ReentrantLock() }
        lock.withLock {
            return self.createReservationTransactional(memberId, productId, quantity, "reentrant")
        }
    }

    // ========================
    // 2. DB Pessimistic Lock
    // ========================
    @Transactional
    fun createReservationWithPessimisticLock(memberId: Long, productId: Long, quantity: Int): OrderReservation {
        checkDuplicate(memberId, productId)
        if (orderRepository.existsPaidOrder(memberId, productId)) throw AlreadyPurchasedException(memberId, productId)
        val product = productRepository.findByIdWithPessimisticLock(productId) ?: throw ProductNotFoundException(productId)
        validateAndDecreaseStock(product, quantity)
        return reservationRepository.save(OrderReservation.create(memberId, product, quantity, "pessimistic:${UUID.randomUUID()}"))
    }

    // ========================
    // 3. Redis 분산락
    // ========================
    @DistributedLock(key = "'order:product:' + #productId", waitTime = 60, leaseTime = 30)
    fun createReservationWithDistributedLock(memberId: Long, productId: Long, quantity: Int): OrderReservation {
        checkDuplicate(memberId, productId)
        if (orderRepository.existsPaidOrder(memberId, productId)) throw AlreadyPurchasedException(memberId, productId)
        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)
        validateAndDecreaseStock(product, quantity)
        return reservationRepository.save(OrderReservation.create(memberId, product, quantity, "distributed:${UUID.randomUUID()}"))
    }

    // ========================
    // 4. SKIP LOCKED (product_stock row per unit)
    // ========================
    @Transactional
    fun createReservationWithSkipLocked(memberId: Long, productId: Long, quantity: Int): OrderReservation {
        checkDuplicate(memberId, productId)
        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)
        if (product.isHotDeal && !product.isHotDealActive()) throw HotDealNotActiveException(productId)
        if (orderRepository.existsPaidOrder(memberId, productId)) throw AlreadyPurchasedException(memberId, productId)

        repeat(quantity) {
            productStockRepository.claimWithSkipLocked(productId, memberId)
                ?: throw OutOfStockException(productId, quantity, 0)
        }
        return reservationRepository.save(OrderReservation.create(memberId, product, quantity, "skip-locked:${UUID.randomUUID()}"))
    }

    // ========================
    // 5. CAS (AtomicInteger, lock-free, 단일 인스턴스)
    // ========================
    @Transactional
    fun createReservationWithCas(memberId: Long, productId: Long, quantity: Int): OrderReservation {
        checkDuplicate(memberId, productId)
        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)
        if (product.isHotDeal && !product.isHotDealActive()) throw HotDealNotActiveException(productId)
        if (orderRepository.existsPaidOrder(memberId, productId)) throw AlreadyPurchasedException(memberId, productId)

        if (!stockCasManager.tryDecrement(productId, quantity))
            throw OutOfStockException(productId, quantity, 0)

        return reservationRepository.save(OrderReservation.create(memberId, product, quantity, "cas:${UUID.randomUUID()}"))
    }

    // ========================
    // 6. Redis Token Queue (LPOP 원자적 선점)
    // ========================
    @Transactional
    fun createReservationWithRedisQueue(memberId: Long, productId: Long, quantity: Int): OrderReservation {
        checkDuplicate(memberId, productId)
        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)
        if (product.isHotDeal && !product.isHotDealActive()) throw HotDealNotActiveException(productId)
        if (orderRepository.existsPaidOrder(memberId, productId)) throw AlreadyPurchasedException(memberId, productId)

        repeat(quantity) {
            redisTokenQueueManager.claim(productId)
                ?: throw OutOfStockException(productId, quantity, 0)
        }
        return reservationRepository.save(OrderReservation.create(memberId, product, quantity, "redis-queue:${UUID.randomUUID()}"))
    }

    // ========================
    // 선점 취소 (PENDING → CANCELLED, 재고 복구 O)
    // ========================
    @Transactional
    fun cancelReservation(reservationId: Long): OrderReservation {
        val reservation = getReservation(reservationId)
        val cancelled = reservation.cancel()
        restoreStock(cancelled)
        return reservationRepository.save(cancelled)
    }

    // ========================
    // 확정 주문 취소 (환불 후 호출, 재고 복구 X)
    // ========================
    @Transactional
    fun cancelOrder(reservationId: Long) {
        orderRepository.findByReservationId(reservationId) ?: throw OrderNotFoundException(reservationId)
    }

    // ========================
    // 결제 성공 → 확정 주문 생성
    // ========================
    @Transactional
    fun confirmReservation(reservationId: Long): Order {
        val reservation = getReservation(reservationId)

        if (reservation.status != ReservationStatus.PENDING)
            throw InvalidOrderStatusException(reservationId, reservation.status.name, "PENDING")
        if (reservation.isExpired()) throw ReservationExpiredException(reservationId)
        if (orderRepository.existsPaidOrder(reservation.memberId, reservation.productId))
            throw AlreadyPurchasedException(reservation.memberId, reservation.productId)

        return orderRepository.save(Order.from(reservation))
    }

    // ========================
    // 만료 처리 스케줄러
    // ========================
    @Transactional
    fun expireReservations(): Int {
        val expired = reservationRepository.findExpiredPendingReservations()
        expired.forEach { reservation ->
            try {
                restoreStock(reservation)
                reservationRepository.save(reservation.expire())
            } catch (_: Exception) {
            }
        }
        return expired.size
    }

    // ========================
    // 내부 헬퍼
    // ========================
    @Transactional
    fun createReservationTransactional(memberId: Long, productId: Long, quantity: Int, prefix: String): OrderReservation {
        checkDuplicate(memberId, productId)
        if (orderRepository.existsPaidOrder(memberId, productId)) throw AlreadyPurchasedException(memberId, productId)
        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)
        validateAndDecreaseStock(product, quantity)
        return reservationRepository.save(OrderReservation.create(memberId, product, quantity, "$prefix:${UUID.randomUUID()}"))
    }

    private fun checkDuplicate(memberId: Long, productId: Long) {
        if (reservationRepository.existsPendingReservation(memberId, productId))
            throw DuplicatePendingOrderException(memberId, productId)
    }

    private fun validateAndDecreaseStock(product: Product, quantity: Int) {
        if (product.isHotDeal && !product.isHotDealActive()) throw HotDealNotActiveException(product.id)
        product.decreaseStock(quantity)
        productRepository.save(product)
    }

    private fun restoreStock(reservation: OrderReservation) {
        when {
            reservation.reservationKey.startsWith("cas:") ->
                stockCasManager.increment(reservation.productId, reservation.quantity)
            reservation.reservationKey.startsWith("redis-queue:") ->
                repeat(reservation.quantity) { redisTokenQueueManager.release(reservation.productId) }
            reservation.reservationKey.startsWith("skip-locked:") -> Unit
            else -> {
                val product = productRepository.findById(reservation.productId) ?: return
                product.increaseStock(reservation.quantity)
                productRepository.save(product)
            }
        }
    }
}
