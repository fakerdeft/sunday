package com.sunday.order.application

import com.sunday.order.domain.Order
import com.sunday.order.domain.Product
import com.sunday.order.exception.DuplicatePendingOrderException
import com.sunday.order.exception.HotDealNotActiveException
import com.sunday.order.exception.OrderNotFoundException
import com.sunday.order.exception.OutOfStockException
import com.sunday.order.exception.ProductNotFoundException
import com.sunday.order.port.inbound.OrderUseCase
import com.sunday.order.port.outbound.OrderRepository
import com.sunday.order.port.outbound.OrderStreamPublisher
import com.sunday.order.port.outbound.ProductRepository
import com.sunday.order.port.outbound.StockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class OrderService(
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val stockRepository: StockRepository,
    private val orderStreamPublisher: OrderStreamPublisher
) : OrderUseCase {

    companion object {
        private const val RESERVATION_TTL_SECONDS = 300L // 5분
    }

    @Transactional(readOnly = true)
    override fun getProducts(): List<Product> {
        return productRepository.findAll()
    }

    @Transactional(readOnly = true)
    override fun getHotDeals(): List<Product> {
        return productRepository.findHotDeals()
    }

    @Transactional(readOnly = true)
    override fun getProduct(productId: Long): Product {
        return productRepository.findById(productId)
            ?: throw ProductNotFoundException(productId)
    }

    @Transactional(readOnly = true)
    override fun getStock(productId: Long): Int {
        val product = productRepository.findById(productId)
            ?: throw ProductNotFoundException(productId)
        return product.stock
    }

    /**
     * 주문 생성 (비관적 락)
     */
    @Transactional
    override fun createOrderWithPessimisticLock(memberId: Long, productId: Long, quantity: Int): Order {
        // 1. 중복 주문 체크
        if (orderRepository.existsPendingOrder(memberId, productId)) {
            throw DuplicatePendingOrderException(memberId, productId)
        }

        // 2. 상품 조회
        val product = productRepository.findByIdWithPessimisticLock(productId)
            ?: throw ProductNotFoundException(productId)

        // 3. 핫딜 활성화 확인
        if (product.isHotDeal && !product.isHotDealActive()) {
            throw HotDealNotActiveException(productId)
        }

        // 4. 재고 확인 및 차감
        product.decreaseStock(quantity)

        productRepository.save(product)

        // 5. Order 저장
        // Redis 예약 키 대신 UUID 등을 임시로 사용하거나, 비관적 락 방식에서는 예약 키 개념이 다를 수 있음
        // 여기서는 단순화를 위해 UUID 사용
        val reservationKey = "db-lock:${UUID.randomUUID()}"

        val order = Order.create(
            memberId = memberId,
            product = product,
            quantity = quantity,
            reservationKey = reservationKey
        )

        return orderRepository.save(order)
    }

    /**
     * 주문 생성
     */
    @Transactional
    override fun createOrderWithDistributedLock(memberId: Long, productId: Long, quantity: Int): Order {
        // 1. 중복 주문 체크
        if (orderRepository.existsPendingOrder(memberId, productId)) {
            throw DuplicatePendingOrderException(memberId, productId)
        }

        // 2. 상품 조회
        val product = productRepository.findById(productId)
            ?: throw ProductNotFoundException(productId)

        // 3. 핫딜 활성화 확인
        if (product.isHotDeal && !product.isHotDealActive()) {
            throw HotDealNotActiveException(productId)
        }

        // 4. 재고 확인 및 차감
        if (product.stock < quantity) {
            throw OutOfStockException(productId, quantity, product.stock)
        }
        product.decreaseStock(quantity)
        productRepository.save(product)

        // 5. Order 저장
        val reservationKey = "distributed-lock:${UUID.randomUUID()}"
        val order = Order.create(
            memberId = memberId,
            product = product,
            quantity = quantity,
            reservationKey = reservationKey
        )

        return orderRepository.save(order)
    }

    /**
     * 주문 생성 비동기 (Lua Script 아토믹 처리)
     * - Lua 스크립트의 원자성으로 처리
     * - 중복 체크 + 재고 차감 + Stream 발행
     * - Consumer가 비동기로 DB에 주문 저장
     */
    override fun createOrderAsync(memberId: Long, productId: Long, quantity: Int): String {
        // 1. 선점 키 생성
        val reservationKey = "async:${UUID.randomUUID()}"

        // 2. Lua Script 아토믹 처리 (Redis Hash에서 상품 정보 조회 + 중복체크 + 재고차감 + Stream 발행)
        val result = stockRepository.processOrderAtomic(
            productId = productId,
            memberId = memberId,
            quantity = quantity,
            reservationKey = reservationKey
        )

        return when (result) {
            1 -> reservationKey  // 성공
            0 -> throw OutOfStockException(productId, quantity, stockRepository.getStock(productId))
            -1 -> throw DuplicatePendingOrderException(memberId, productId)
            -2 -> throw ProductNotFoundException(productId)  // Redis에 상품 정보 없음
            else -> throw RuntimeException("Unexpected result from processOrderAtomic: $result")
        }
    }

    @Transactional(readOnly = true)
    override fun getOrder(orderId: Long): Order {
        return orderRepository.findById(orderId)
            ?: throw OrderNotFoundException(orderId)
    }

    @Transactional(readOnly = true)
    override fun getMyOrders(memberId: Long): List<Order> {
        return orderRepository.findByMemberId(memberId)
    }

    /**
     * 주문 취소
     */
    @Transactional
    override fun cancelOrder(orderId: Long): Order {
        val order = getOrder(orderId)

        // 취소 처리
        val cancelledOrder = order.markAsCancelled()

        // 상품 조회 및 재고 복구
        val product = getProduct(order.productId)
        product.increaseStock(order.quantity)
        productRepository.save(product)

        return orderRepository.save(cancelledOrder)
    }

    /**
     * 결제 완료 처리
     */
    @Transactional
    override fun markOrderAsPaid(orderId: Long): Order {
        val order = getOrder(orderId)

        // 결제 완료 처리
        val paidOrder = order.markAsPaid()

        return orderRepository.save(paidOrder)
    }

    /**
     * 만료된 주문 처리
     */
    @Transactional
    override fun expireOrders(): Int {
        val expiredOrders = orderRepository.findExpiredPendingOrders()

        expiredOrders.forEach { order ->
            try {
                // 상품 조회 및 재고 복구 (DB)
                val product = getProduct(order.productId)
                product.increaseStock(order.quantity)
                productRepository.save(product)

                // 주문 상태 변경
                val expiredOrder = order.markAsExpired()
                orderRepository.save(expiredOrder)
            } catch (e: Exception) {
                // 개별 주문 처리 실패 시 로그 남기고 계속 진행
                // log.error("Failed to expire order ${order.id}", e)
            }
        }

        return expiredOrders.size
    }
}
