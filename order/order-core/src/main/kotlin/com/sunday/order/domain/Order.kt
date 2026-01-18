package com.sunday.order.domain

import com.sunday.order.exception.InvalidOrderQuantityException
import com.sunday.order.exception.InvalidOrderStatusException
import com.sunday.order.exception.InvalidProductPriceException
import com.sunday.order.exception.OrderExpiredException
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 주문 도메인 모델
 *
 * - 도메인이 스스로 상태 전이 검증
 * - Service에서 중복 검증 불필요
 */
data class Order(
    val id: Long,
    val memberId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalAmount: BigDecimal,
    val status: OrderStatus,
    val reservationKey: String,
    val expireAt: LocalDateTime,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        if (quantity <= 0) {
            throw InvalidOrderQuantityException(quantity)
        }

        if (unitPrice <= BigDecimal.ZERO) {
            throw InvalidProductPriceException(unitPrice)
        }
    }

    companion object {
        private const val RESERVATION_TIMEOUT_MINUTES = 5L

        fun create(
            memberId: Long,
            product: Product,
            quantity: Int,
            reservationKey: String
        ): Order {
            val now = LocalDateTime.now()

            return Order(
                id = 0L,
                memberId = memberId,
                productId = product.id,
                productName = product.name,
                quantity = quantity,
                unitPrice = product.price,
                totalAmount = product.price * BigDecimal(quantity),
                status = OrderStatus.PENDING,
                reservationKey = reservationKey,
                expireAt = now.plusMinutes(RESERVATION_TIMEOUT_MINUTES),
                createdAt = now,
                updatedAt = now
            )
        }
    }

    fun isExpired(): Boolean {
        return LocalDateTime.now().isAfter(expireAt)
    }

    /**
     * 결제 완료 처리
     *
     * @throws InvalidOrderStatusException PENDING이 아닌 경우
     * @throws OrderExpiredException 만료된 경우
     */
    fun markAsPaid(): Order {
        if (status != OrderStatus.PENDING) {
            throw InvalidOrderStatusException(id, status.name, "PENDING")
        }

        if (isExpired()) {
            throw OrderExpiredException(id)
        }

        return copy(
            status = OrderStatus.PAID,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 주문 취소
     *
     * @throws InvalidOrderStatusException PENDING 또는 PAID가 아닌 경우
     */
    fun markAsCancelled(): Order {
        // PENDING(결제 전 취소) 또는 PAID(환불) 상태에서만 취소 가능
        if (status != OrderStatus.PENDING && status != OrderStatus.PAID) {
            throw InvalidOrderStatusException(id, status.name, "PENDING or PAID")
        }

        return copy(
            status = OrderStatus.CANCELLED,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 만료 처리
     *
     * @throws InvalidOrderStatusException PENDING이 아닌 경우
     */
    fun markAsExpired(): Order {
        if (status != OrderStatus.PENDING) {
            throw InvalidOrderStatusException(id, status.name, "PENDING")
        }

        return copy(
            status = OrderStatus.EXPIRED,
            updatedAt = LocalDateTime.now()
        )
    }
}
