package com.sunday.gate.api

import com.sunday.common.auth.UserId
import com.sunday.gate.api.dto.OrderPassResponse
import com.sunday.gate.application.OrderPassService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/order-pass")
class OrderPassController(
    private val orderPassService: OrderPassService
) {
    @PostMapping("/{productId}")
    fun requestPass(
        @UserId memberId: Long,
        @PathVariable productId: Long
    ): OrderPassResponse =
        OrderPassResponse.from(orderPassService.requestPass(productId, memberId))

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun release(
        @UserId memberId: Long,
        @PathVariable productId: Long
    ) = orderPassService.release(productId, memberId)
}
