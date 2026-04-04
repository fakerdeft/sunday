package com.sunday.order.presentation

import com.sunday.common.lock.DistributedLock
import com.sunday.order.application.OrderService
import com.sunday.order.application.TestService
import com.sunday.order.presentation.dto.AsyncOrderResponse
import com.sunday.order.presentation.dto.CreateOrderRequest
import com.sunday.order.presentation.dto.OrderResponse
import com.sunday.order.presentation.dto.ProductResponse
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
    private val testService: TestService
) {

    @GetMapping("/products")
    @ResponseStatus(HttpStatus.OK)
    fun getProducts(): List<ProductResponse> {
        return orderService.getProducts().map { ProductResponse.from(it, orderService.getStock(it.id)) }
    }

    @GetMapping("/products/hot-deals")
    @ResponseStatus(HttpStatus.OK)
    fun getHotDeals(): List<ProductResponse> {
        return orderService.getHotDeals().map { ProductResponse.from(it, orderService.getStock(it.id)) }
    }

    @GetMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.OK)
    fun getProduct(@PathVariable productId: Long): ProductResponse {
        val product = orderService.getProduct(productId)
        return ProductResponse.from(product, orderService.getStock(productId))
    }

    @PostMapping("/pessimistic")
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrderWithPessimistic(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): OrderResponse {
        return OrderResponse.from(orderService.createOrderWithPessimisticLock(userId.toLong(), request.productId, request.quantity))
    }

    @PostMapping("/distributed-lock")
    @ResponseStatus(HttpStatus.CREATED)
    @DistributedLock(key = "'order:product:' + #request.productId", waitTime = 3, leaseTime = 5)
    fun createOrderWithDistributedLock(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): OrderResponse {
        return OrderResponse.from(orderService.createOrderWithDistributedLock(userId.toLong(), request.productId, request.quantity))
    }

    @PostMapping("/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun createOrderAsync(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): AsyncOrderResponse {
        val reservationKey = orderService.createOrderAsync(userId.toLong(), request.productId, request.quantity)
        return AsyncOrderResponse(reservationKey = reservationKey)
    }

    @GetMapping("/{orderId}")
    @ResponseStatus(HttpStatus.OK)
    fun getOrder(@PathVariable orderId: Long): OrderResponse {
        return OrderResponse.from(orderService.getOrder(orderId))
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun getMyOrders(@RequestHeader("X-USER-ID") userId: String): List<OrderResponse> {
        return orderService.getMyOrders(userId.toLong()).map { OrderResponse.from(it) }
    }

    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.OK)
    fun cancelOrder(@PathVariable orderId: Long): OrderResponse {
        return OrderResponse.from(orderService.cancelOrder(orderId))
    }

    @PostMapping("/{orderId}/mark-paid")
    @ResponseStatus(HttpStatus.OK)
    fun markOrderAsPaid(@PathVariable orderId: Long): OrderResponse {
        return OrderResponse.from(orderService.markOrderAsPaid(orderId))
    }

    @PostMapping("/test/reset")
    fun resetData() {
        testService.resetAllData()
    }
}
