package com.sunday.order.benchmark

import com.sunday.common.auth.UserId
import com.sunday.order.api.dto.CreateOrderRequest
import com.sunday.order.api.dto.ReservationResponse
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 비교 측정 전용. `local` 프로필에서만 등록된다. */
@Profile("local")
@RestController
@RequestMapping("/load-tests/orders")
class OrderBenchmarkController(
    private val benchmarkService: OrderBenchmarkService
) {
    @PostMapping("/setup")
    fun prepareProduct(@Valid @RequestBody request: BenchmarkSetupRequest) =
        benchmarkService.prepareProduct(request.productId, request.quantity)

    @PostMapping("/reservations/skip-locked")
    @ResponseStatus(HttpStatus.CREATED)
    fun createWithSkipLocked(
        @UserId memberId: Long,
        @Valid @RequestBody request: CreateOrderRequest
    ): ReservationResponse = ReservationResponse.from(
        benchmarkService.createWithSkipLocked(memberId, request.productId, request.quantity)
    )

    @PostMapping("/reservations/pessimistic")
    @ResponseStatus(HttpStatus.CREATED)
    fun createWithPessimisticLock(
        @UserId memberId: Long,
        @Valid @RequestBody request: CreateOrderRequest
    ): ReservationResponse = ReservationResponse.from(
        benchmarkService.createWithPessimisticLock(memberId, request.productId, request.quantity)
    )

    @GetMapping("/products/{productId}/state")
    fun getState(@PathVariable productId: Long): BenchmarkState =
        benchmarkService.getState(productId)
}
