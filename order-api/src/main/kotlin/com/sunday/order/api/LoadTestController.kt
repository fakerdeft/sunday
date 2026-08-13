package com.sunday.order.api

import com.sunday.order.application.OrderService
import com.sunday.order.application.TestService
import com.sunday.order.api.dto.CreateOrderRequest
import com.sunday.order.api.dto.ReservationResponse
import com.sunday.order.api.dto.ScaleResetRequest
import com.sunday.common.auth.UserId
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Profile("local")
@RestController
@RequestMapping("/internal/load-test/orders")
class LoadTestController(
    private val orderService: OrderService,
    private val testService: TestService
) {
    @PostMapping("/pessimistic")
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

    @PostMapping("/reset")
    fun resetData() = testService.resetAllData()

    @PostMapping("/reset-scale")
    fun resetDataForScale(@Valid @RequestBody request: ScaleResetRequest) =
        testService.resetProductForScale(request.productId, request.quantity)

    @GetMapping("/state")
    fun getState(@RequestParam productId: Long) = testService.getState(productId)
}
