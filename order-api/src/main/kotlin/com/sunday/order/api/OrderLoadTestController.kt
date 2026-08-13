package com.sunday.order.api

import com.sunday.common.auth.UserId
import com.sunday.order.api.dto.CreateOrderRequest
import com.sunday.order.api.dto.OrderLoadTestSetupRequest
import com.sunday.order.api.dto.ReservationResponse
import com.sunday.order.application.LoadTestState
import com.sunday.order.application.OrderLoadTestService
import com.sunday.order.application.OrderService
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

@Profile("local")
@RestController
@RequestMapping("/load-tests/orders")
class OrderLoadTestController(
    private val orderService: OrderService,
    private val loadTestService: OrderLoadTestService
) {
    @PostMapping("/setup")
    fun prepareProduct(@Valid @RequestBody request: OrderLoadTestSetupRequest) =
        loadTestService.prepareProduct(request.productId, request.quantity)

    @PostMapping("/reservations/skip-locked")
    @ResponseStatus(HttpStatus.CREATED)
    fun createWithSkipLocked(
        @UserId memberId: Long,
        @Valid @RequestBody request: CreateOrderRequest
    ): ReservationResponse = ReservationResponse.from(
        orderService.createReservationWithSkipLocked(memberId, request.productId, request.quantity)
    )

    @PostMapping("/reservations/pessimistic")
    @ResponseStatus(HttpStatus.CREATED)
    fun createWithPessimisticLock(
        @UserId memberId: Long,
        @Valid @RequestBody request: CreateOrderRequest
    ): ReservationResponse = ReservationResponse.from(
        orderService.createReservationWithPessimisticLock(
            memberId,
            request.productId,
            request.quantity
        )
    )

    @GetMapping("/products/{productId}/state")
    fun getState(@PathVariable productId: Long): LoadTestState =
        loadTestService.getState(productId)
}
