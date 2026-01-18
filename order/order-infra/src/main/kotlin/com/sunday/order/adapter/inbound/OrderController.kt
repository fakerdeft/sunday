package com.sunday.order.adapter.inbound

import com.sunday.order.adapter.inbound.dto.CreateOrderRequest
import com.sunday.order.adapter.inbound.dto.OrderResponse
import com.sunday.order.adapter.inbound.dto.ProductResponse
import com.sunday.order.application.OrderService
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
    private val orderService: OrderService
) {
    @GetMapping("/products")
    @ResponseStatus(HttpStatus.OK)
    fun getProducts(): List<ProductResponse> {
        val products = orderService.getProducts()

        return products.map { ProductResponse.from(it, orderService.getStock(it.id)) }
    }

    @GetMapping("/products/hot-deals")
    @ResponseStatus(HttpStatus.OK)
    fun getHotDeals(): List<ProductResponse> {
        val products = orderService.getHotDeals()

        return products.map { ProductResponse.from(it, orderService.getStock(it.id)) }
    }

    @GetMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.OK)
    fun getProduct(@PathVariable productId: Long): ProductResponse {
        val product = orderService.getProduct(productId)
        val stock = orderService.getStock(productId)

        return ProductResponse.from(product, stock)
    }

    /**
     * 주문 생성 (재고 선점)
     *
     * - 같은 상품에 대한 PENDING 주문이 있으면 거부 (중복 주문 방지)
     * - Redis DECR로 원자적 재고 차감
     * - 5분 TTL 선점
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): OrderResponse {
        val memberId = userId.toLong()
        val order = orderService.createOrder(memberId, request.productId, request.quantity)

        return OrderResponse.from(order)
    }

    @GetMapping("/{orderId}")
    @ResponseStatus(HttpStatus.OK)
    fun getOrder(@PathVariable orderId: Long): OrderResponse {
        val order = orderService.getOrder(orderId)

        return OrderResponse.from(order)
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun getMyOrders(@RequestHeader("X-USER-ID") userId: String): List<OrderResponse> {
        val memberId = userId.toLong()
        val orders = orderService.getMyOrders(memberId)

        return orders.map { OrderResponse.from(it) }
    }

    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.OK)
    fun cancelOrder(@PathVariable orderId: Long): OrderResponse {
        val order = orderService.cancelOrder(orderId)

        return OrderResponse.from(order)
    }
}
