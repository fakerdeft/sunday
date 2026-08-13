package com.sunday.order.config.scheduler

import com.sunday.order.application.OrderService
import org.apache.logging.log4j.LogManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OrderExpirationScheduler(private val orderService: OrderService) {
    private val log = LogManager.getLogger(javaClass)

    @Scheduled(fixedRate = 60_000)
    fun expireReservations() {
        try {
            val count = orderService.expireReservations()

            if (count > 0) log.info("만료 예약 ${count}건의 재고를 복구했습니다")
        } catch (e: Exception) {
            log.error("만료 예약 처리에 실패했습니다", e)
        }
    }
}
