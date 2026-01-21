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
import com.sunday.order.port.outbound.ProductRepository
import com.sunday.order.port.outbound.StockRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val stockRepository: StockRepository
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
        return stockRepository.getStock(productId)
    }

    /**
     * 주문 생성 (재고 선점)
     *
     * 1. 중복 주문 체크
     * 2. 상품 조회
     * 3. 핫딜 활성화 확인
     * 4. Redis에서 재고 차감
     * 5. 선점 키 생성
     * 6. Order 저장
     */
    @Transactional
    override fun createOrder(memberId: Long, productId: Long, quantity: Int): Order {
        // 1. 중복 주문 체크 (같은 상품에 대한 PENDING 주문이 있는지)
        if (orderRepository.existsPendingOrder(memberId, productId)) {
            throw DuplicatePendingOrderException(memberId, productId)
        }

        // 2. 상품 조회
        val product = getProduct(productId)

        // 3. 핫딜 활성화 확인
        if (product.isHotDeal && !product.isHotDealActive()) {
            throw HotDealNotActiveException(productId)
        }

        // 4. Redis 재고 차감
        stockRepository.decreaseStock(productId, quantity)
            ?: throw OutOfStockException(productId, quantity, stockRepository.getStock(productId))

        try {
            // 4. 선점 키 생성
            val reservationKey = stockRepository.createReservation(
                productId = productId,
                memberId = memberId,
                quantity = quantity,
                ttlSeconds = RESERVATION_TTL_SECONDS
            )

            // 5. Order 저장
            val order = Order.create(
                memberId = memberId,
                product = product,
                quantity = quantity,
                reservationKey = reservationKey
            )

            return orderRepository.save(order)
        } catch (e: DataIntegrityViolationException) {
            // Unique Index 위반 (동시 요청으로 인한 중복 주문)
            stockRepository.increaseStock(productId, quantity)
            throw DuplicatePendingOrderException(memberId, productId)
        } catch (e: Exception) {
            // 기타 실패 시 재고 복구
            stockRepository.increaseStock(productId, quantity)
            throw e
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
     * 주문 취소 (재고 복구)
     */
    @Transactional
    override fun cancelOrder(orderId: Long): Order {
        val order = getOrder(orderId)

        // 취소 처리
        val cancelledOrder = order.markAsCancelled()

        // 상품 정보 조회 (최대 재고 확인용)
        val product = getProduct(order.productId)

        // 재고 복구 (최대 재고 제한 적용)
        // totalQuantity를 기준으로 최대 재고 초과 방지
        stockRepository.increaseStock(order.productId, order.quantity, product.totalQuantity)

        // 선점 해제
        stockRepository.releaseReservation(order.reservationKey)

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

        // 선점 해제 (결제 완료로 더 이상 필요 없음)
        stockRepository.releaseReservation(order.reservationKey)

        return orderRepository.save(paidOrder)
    }

    /**
     * 만료된 주문 처리 (스케줄러에서 호출)
     */
    @Transactional
    override fun expireOrders(): Int {
        val expiredOrders = orderRepository.findExpiredPendingOrders()

        expiredOrders.forEach { order ->
            try {
                // 상품 정보 조회 (최대 재고 확인용)
                val product = getProduct(order.productId)

                // 재고 복구 (최대 재고 제한 적용)
                // totalQuantity를 기준으로 최대 재고 초과 방지
                stockRepository.increaseStock(order.productId, order.quantity, product.totalQuantity)

                // 선점 해제
                stockRepository.releaseReservation(order.reservationKey)

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
