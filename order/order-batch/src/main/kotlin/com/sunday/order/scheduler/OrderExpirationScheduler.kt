package com.sunday.order.scheduler

import com.sunday.order.application.OrderService
import org.apache.logging.log4j.LogManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 만료된 주문 처리 스케줄러
 *
 * - PENDING 상태이면서 expireAt이 지난 주문을 EXPIRED 처리
 * - 재고 복구
 */
@Component
class OrderExpirationScheduler(
    private val orderService: OrderService
) {
    private val log = LogManager.getLogger(javaClass)

    /**
     * 1분마다 실행
     */
    @Scheduled(fixedRate = 60_000)
    fun expireOrders() {
        try {
            val count = orderService.expireOrders()

            if (count > 0) {
                log.info("Expired $count orders and restored stock")
            }
        } catch (e: Exception) {
            log.error("Failed to expire orders", e)
        }
    }
}