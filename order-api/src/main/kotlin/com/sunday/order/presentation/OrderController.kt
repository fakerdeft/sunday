package com.sunday.order.presentation

import com.sunday.support.infra.lock.DistributedLock
import com.sunday.order.application.OrderService
import com.sunday.order.application.TestService
import com.sunday.order.presentation.dto.CreateOrderRequest
import com.sunday.order.presentation.dto.OrderResponse
import com.sunday.order.presentation.dto.ProductResponse
import com.sunday.order.presentation.dto.ReservationResponse
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

    // ========================
    // 상품 조회
    // ========================

    @GetMapping("/products")
    fun getProducts(): List<ProductResponse> =
        orderService.getProducts().map { ProductResponse.from(it) }

    @GetMapping("/products/hot-deals")
    fun getHotDeals(): List<ProductResponse> =
        orderService.getHotDeals().map { ProductResponse.from(it) }

    @GetMapping("/products/{productId}")
    fun getProduct(@PathVariable productId: Long): ProductResponse =
        ProductResponse.from(orderService.getProduct(productId))

    // ========================
    // 선점(Reservation) 생성 — 6가지 방식
    // ========================

    @PostMapping("/reentrant-lock")
    @ResponseStatus(HttpStatus.CREATED)
    fun createWithReentrantLock(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): ReservationResponse =
        ReservationResponse.from(orderService.createReservationWithReentrantLock(userId.toLong(), request.productId, request.quantity))

    @PostMapping("/pessimistic")
    @ResponseStatus(HttpStatus.CREATED)
    fun createWithPessimistic(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): ReservationResponse =
        ReservationResponse.from(orderService.createReservationWithPessimisticLock(userId.toLong(), request.productId, request.quantity))

    @PostMapping("/distributed-lock")
    @ResponseStatus(HttpStatus.CREATED)
    fun createWithDistributedLock(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): ReservationResponse =
        ReservationResponse.from(orderService.createReservationWithDistributedLock(userId.toLong(), request.productId, request.quantity))

    @PostMapping("/skip-locked")
    @ResponseStatus(HttpStatus.CREATED)
    fun createWithSkipLocked(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): ReservationResponse =
        ReservationResponse.from(orderService.createReservationWithSkipLocked(userId.toLong(), request.productId, request.quantity))

    @PostMapping("/cas")
    @ResponseStatus(HttpStatus.CREATED)
    fun createWithCas(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): ReservationResponse =
        ReservationResponse.from(orderService.createReservationWithCas(userId.toLong(), request.productId, request.quantity))

    @PostMapping("/redis-queue")
    @ResponseStatus(HttpStatus.CREATED)
    fun createWithRedisQueue(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: CreateOrderRequest
    ): ReservationResponse =
        ReservationResponse.from(orderService.createReservationWithRedisQueue(userId.toLong(), request.productId, request.quantity))

    // ========================
    // 선점/주문 조회
    // ========================

    @GetMapping("/reservations/{reservationId}")
    fun getReservation(@PathVariable reservationId: Long): ReservationResponse =
        ReservationResponse.from(orderService.getReservation(reservationId))

    @GetMapping("/reservations/me")
    fun getMyReservations(@RequestHeader("X-USER-ID") userId: String): List<ReservationResponse> =
        orderService.getMyReservations(userId.toLong()).map { ReservationResponse.from(it) }

    @GetMapping("/{reservationId}")
    fun getOrder(@PathVariable reservationId: Long): OrderResponse =
        OrderResponse.from(orderService.getOrder(reservationId))

    @GetMapping("/me")
    fun getMyOrders(@RequestHeader("X-USER-ID") userId: String): List<OrderResponse> =
        orderService.getMyOrders(userId.toLong()).map { OrderResponse.from(it) }

    // ========================
    // 선점 취소 (PENDING → CANCELLED, 재고 복구 O)
    // ========================

    @PostMapping("/reservations/{reservationId}/cancel")
    fun cancelReservation(@PathVariable reservationId: Long): ReservationResponse =
        ReservationResponse.from(orderService.cancelReservation(reservationId))

    // ========================
    // 결제 성공 → 확정 주문 생성 (payment-api 호출용)
    // ========================

    @PostMapping("/reservations/{reservationId}/confirm")
    fun confirmReservation(@PathVariable reservationId: Long): OrderResponse =
        OrderResponse.from(orderService.confirmReservation(reservationId))

    // ========================
    // 확정 주문 취소 (환불 후 호출, 재고 복구 X)
    // ========================

    @PostMapping("/{reservationId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelOrder(@PathVariable reservationId: Long) =
        orderService.cancelOrder(reservationId)

    // ========================
    // 테스트 데이터 초기화
    // ========================

    @PostMapping("/test/reset")
    fun resetData() = testService.resetAllData()

    @PostMapping("/test/reset-scale")
    fun resetDataForScale(
        @RequestBody request: ScaleResetRequest
    ) = testService.resetProductForScale(request.productId, request.quantity)

    data class ScaleResetRequest(val productId: Long, val quantity: Int)
}
