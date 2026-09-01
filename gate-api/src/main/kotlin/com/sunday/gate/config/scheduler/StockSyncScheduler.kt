package com.sunday.gate.config.scheduler

import com.sunday.gate.application.OrderPassService
import com.sunday.gate.client.OrderApiClient
import com.sunday.gate.config.OrderPassProperties
import org.apache.logging.log4j.LogManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 주문 서버 재고를 읽어 budget 을 재계산한다.
 * 호출은 주기당 상품 하나에 한 번. 대기 트래픽과 무관하게 고정된다.
 */
@Component
class StockSyncScheduler(
    private val properties: OrderPassProperties,
    private val orderPassService: OrderPassService,
    private val orderApiClient: OrderApiClient
) {
    private val log = LogManager.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${sunday.order-pass.sync-delay-ms:500}")
    fun syncBudgets() {
        properties.managedProductIds.forEach { productId ->
            try {
                sync(productId)
            } catch (e: Exception) {
                log.error("상품 {} 재고 동기화에 실패했습니다", productId, e)
            }
        }
    }

    fun sync(productId: Long) {
        val previous = orderPassService.budget(productId)
        val snapshot = orderApiClient.getStockSnapshot(productId)
        val budget = orderPassService.syncBudget(productId, snapshot.availableStock)

        if (previous <= 0 && budget > 0) {
            log.info("상품 {} 재고가 복구되어 통행증 {}장을 다시 발급할 수 있습니다", productId, budget)
        }
    }
}
