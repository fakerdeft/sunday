package com.sunday.order.benchmark.stream

import com.sunday.common.auth.UserId
import com.sunday.order.api.dto.CreateOrderRequest
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Redis Streams 접수 큐의 이전 구현이다. 대기열이 입장 제어 방식으로 바뀐 뒤에는
 * 두 방식을 비교 측정하기 위해서만 남겨 두었으며 `local` 프로필에서만 등록된다.
 */
@Profile("local")
@RestController
@RequestMapping("/load-tests/orders")
class OrderStreamQueueController(
    private val orderQueueService: OrderQueueService
) {
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun createOrderRequest(
        @UserId memberId: Long,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateOrderRequest
    ): OrderRequestResponse = OrderRequestResponse.from(
        orderQueueService.enqueue(
            idempotencyKey = idempotencyKey.orEmpty(),
            memberId = memberId,
            productId = request.productId,
            quantity = request.quantity
        )
    )

    @GetMapping("/requests/{requestId}")
    fun getOrderRequest(
        @UserId memberId: Long,
        @PathVariable requestId: String
    ): OrderRequestResponse = OrderRequestResponse.from(orderQueueService.get(requestId, memberId))
}
