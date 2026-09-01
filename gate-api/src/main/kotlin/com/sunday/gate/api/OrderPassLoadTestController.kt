package com.sunday.gate.api

import com.sunday.gate.application.OrderPassService
import com.sunday.gate.config.scheduler.StockSyncScheduler
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 반복 측정용 초기화. `local` 프로필에서만 등록된다. */
@Profile("local")
@RestController
@RequestMapping("/load-tests/order-pass")
class OrderPassLoadTestController(
    private val orderPassService: OrderPassService,
    private val stockSyncScheduler: StockSyncScheduler
) {
    @PostMapping("/{productId}/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset(@PathVariable productId: Long) {
        orderPassService.reset(productId)

        stockSyncScheduler.sync(productId)
    }
}
