package com.sunday.order.adapter.inbound

import com.sunday.common.lock.DistributedLock
import com.sunday.order.adapter.inbound.dto.AsyncOrderResponse
import com.sunday.order.adapter.inbound.dto.CreateOrderRequest
import com.sunday.order.adapter.inbound.dto.OrderResponse
import com.sunday.order.adapter.inbound.dto.ProductResponse
import com.sunday.order.port.inbound.OrderUseCase
import com.sunday.order.port.inbound.TestUseCase
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
    private val orderUseCase: OrderUseCase,
    private val testUseCase: TestUseCase
) {
    @GetMapping("/products")
    @ResponseStatus(HttpStatus.OK)
    fun getProducts(): List<ProductResponse> {
        val products = orderUseCase.getProducts()

        return products.map { ProductResponse.from(it, orderUseCase.getStock(it.id)) }
    }

    @GetMapping("/products/hot-deals")
    @ResponseStatus(HttpStatus.OK)
    fun getHotDeals(): List<ProductResponse> {
        val products = orderUseCase.getHotDeals()

        return products.map { ProductResponse.from(it, orderUseCase.getStock(it.id)) }
    }

    @GetMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.OK)
    fun getProduct(@PathVariable productId: Long): ProductResponse {
        val product = orderUseCase.getProduct(productId)
        val stock = orderUseCase.getStock(productId)

        return ProductResponse.from(product, stock)
    }

    @PostMapping("/pessimistic")
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrderWithPessimistic(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): OrderResponse {
        val memberId = userId.toLong()
        val order = orderUseCase.createOrderWithPessimisticLock(memberId, request.productId, request.quantity)

        return OrderResponse.from(order)
    }

    @PostMapping("/distributed-lock")
    @ResponseStatus(HttpStatus.CREATED)
    @DistributedLock(key = "'order:product:' + #request.productId", waitTime = 3, leaseTime = 5)
    fun createOrderWithDistributedLock(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): OrderResponse {
        val memberId = userId.toLong()
        val order = orderUseCase.createOrderWithDistributedLock(memberId, request.productId, request.quantity)

        return OrderResponse.from(order)
    }

    @PostMapping("/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun createOrderAsync(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): AsyncOrderResponse {
        val memberId = userId.toLong()
        val reservationKey = orderUseCase.createOrderAsync(memberId, request.productId, request.quantity)

        return AsyncOrderResponse(reservationKey = reservationKey)
    }

    @GetMapping("/{orderId}")
    @ResponseStatus(HttpStatus.OK)
    fun getOrder(@PathVariable orderId: Long): OrderResponse {
        val order = orderUseCase.getOrder(orderId)

        return OrderResponse.from(order)
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun getMyOrders(@RequestHeader("X-USER-ID") userId: String): List<OrderResponse> {
        val memberId = userId.toLong()
        val orders = orderUseCase.getMyOrders(memberId)

        return orders.map { OrderResponse.from(it) }
    }

    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.OK)
    fun cancelOrder(@PathVariable orderId: Long): OrderResponse {
        val order = orderUseCase.cancelOrder(orderId)

        return OrderResponse.from(order)
    }

    @PostMapping("/test/reset")
    fun resetData() {
        testUseCase.resetAllData()
    }
}
