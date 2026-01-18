package com.sunday.order.port.inbound

import com.sunday.order.domain.Order
import com.sunday.order.domain.Product

/**
 * Order Use Case (Input Port)
 */
interface OrderUseCase {
    /**
     * 상품 목록 조회
     */
    fun getProducts(): List<Product>

    /**
     * 핫딜 상품 목록 조회
     */
    fun getHotDeals(): List<Product>

    /**
     * 상품 상세 조회
     */
    fun getProduct(productId: Long): Product

    /**
     * 재고 조회 (Redis)
     */
    fun getStock(productId: Long): Int

    /**
     * 주문 생성 (재고 선점)
     * - Redis에서 재고 차감
     * - 5분 TTL로 선점
     * - Order 생성 (PENDING 상태)
     */
    fun createOrder(memberId: Long, productId: Long, quantity: Int): Order

    /**
     * 주문 조회
     */
    fun getOrder(orderId: Long): Order

    /**
     * 내 주문 목록
     */
    fun getMyOrders(memberId: Long): List<Order>

    /**
     * 주문 취소 (재고 복구)
     */
    fun cancelOrder(orderId: Long): Order

    /**
     * 결제 완료 처리
     */
    fun markOrderAsPaid(orderId: Long): Order

    /**
     * 만료된 주문 처리 (스케줄러용)
     * - PENDING 상태이면서 expireAt 지난 주문들 EXPIRED 처리
     * - 재고 복구
     */
    fun expireOrders(): Int
}
