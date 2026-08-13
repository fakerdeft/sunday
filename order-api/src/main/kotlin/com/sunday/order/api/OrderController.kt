package com.sunday.order.api

import com.sunday.order.api.dto.CreateOrderRequest
import com.sunday.order.api.dto.OrderResponse
import com.sunday.order.api.dto.OrderRequestResponse
import com.sunday.order.api.dto.ProductResponse
import com.sunday.order.api.dto.ReservationResponse
import com.sunday.order.application.OrderQueueService
import com.sunday.order.application.OrderService
import com.sunday.common.auth.UserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderQueueService: OrderQueueService
) {
    @GetMapping("/products")
    fun getProducts(): List<ProductResponse> =
        orderService.getProducts().map { ProductResponse.from(it.product, it.availableStock) }

    @GetMapping("/products/hot-deals")
    fun getHotDeals(): List<ProductResponse> =
        orderService.getHotDeals().map { ProductResponse.from(it.product, it.availableStock) }

    @GetMapping("/products/{productId}")
    fun getProduct(@PathVariable productId: Long): ProductResponse {
        val availability = orderService.getProduct(productId)

        return ProductResponse.from(availability.product, availability.availableStock)
    }

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

    @GetMapping("/reservations/me")
    fun getMyReservations(
        @UserId memberId: Long
    ): List<ReservationResponse> =
        orderService.getMyReservations(memberId).map { ReservationResponse.from(it) }

    @GetMapping("/reservations/{reservationId}")
    fun getReservation(@PathVariable reservationId: Long): ReservationResponse =
        ReservationResponse.from(orderService.getReservation(reservationId))

    @PostMapping("/reservations/{reservationId}/cancel")
    fun cancelReservation(@PathVariable reservationId: Long): ReservationResponse =
        ReservationResponse.from(orderService.cancelReservation(reservationId))

    @PostMapping("/reservations/{reservationId}/confirm")
    fun confirmReservation(@PathVariable reservationId: Long): OrderResponse =
        OrderResponse.from(orderService.confirmReservation(reservationId))

    @GetMapping("/me")
    fun getMyOrders(@UserId memberId: Long): List<OrderResponse> =
        orderService.getMyOrders(memberId).map { OrderResponse.from(it) }

    @GetMapping("/{reservationId}")
    fun getOrder(@PathVariable reservationId: Long): OrderResponse =
        OrderResponse.from(orderService.getOrder(reservationId))

    @PostMapping("/{reservationId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelOrder(@PathVariable reservationId: Long) =
        orderService.cancelOrder(reservationId)
}
