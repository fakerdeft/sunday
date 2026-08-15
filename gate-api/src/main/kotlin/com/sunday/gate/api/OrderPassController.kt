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
    /**
     * 주문에 앞서 통행증을 요청한다. 재고가 없으면 여기서 품절로 끝나고 주문 서버까지 가지 않는다.
     */
    @PostMapping("/{productId}")
    fun requestPass(
        @UserId memberId: Long,
        @PathVariable productId: Long
    ): OrderPassResponse =
        OrderPassResponse.from(orderPassService.requestPass(productId, memberId))

    /** 주문을 마쳤거나 포기했을 때 통행증을 반납한다. */
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun release(
        @UserId memberId: Long,
        @PathVariable productId: Long
    ) = orderPassService.release(productId, memberId)
}
