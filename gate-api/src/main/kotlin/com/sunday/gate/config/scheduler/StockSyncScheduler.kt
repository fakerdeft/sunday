package com.sunday.gate.config.scheduler

import com.sunday.gate.application.OrderPassService
import com.sunday.gate.client.OrderApiClient
import com.sunday.gate.config.OrderPassProperties
import org.apache.logging.log4j.LogManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 주문 서버의 재고를 읽어 발급 가능 수량을 다시 계산한다.
 *
 * 호출은 주기당 상품 하나에 한 번뿐이다. 대기 트래픽이 몇 만 건이든
 * 주문 서버로 가는 재고 조회는 상품당 초당 두 건으로 고정된다.
 *
 * 결제 실패나 예약 만료로 재고가 돌아오면 다음 주기에 수량이 올라가고,
 * 그때부터 다시 통행증이 발급된다.
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
