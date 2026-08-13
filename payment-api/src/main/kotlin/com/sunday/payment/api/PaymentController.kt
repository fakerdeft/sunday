package com.sunday.payment.api

import com.sunday.common.auth.UserId
import com.sunday.payment.application.PaymentService
import com.sunday.payment.api.dto.PaymentResponse
import com.sunday.payment.api.dto.ProcessPaymentRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun processPayment(
        @UserId memberId: Long,
        @Valid @RequestBody request: ProcessPaymentRequest
    ): PaymentResponse {
        return PaymentResponse.from(
            paymentService.processPayment(
                reservationId = request.orderId,
                memberId = memberId,
                idempotencyKey = request.idempotencyKey
            )
        )
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun getMyPayments(@UserId memberId: Long): List<PaymentResponse> {
        return paymentService.getMyPayments(memberId).map { PaymentResponse.from(it) }
    }

    @GetMapping("/order/{orderId}")
    @ResponseStatus(HttpStatus.OK)
    fun getPaymentByOrderId(@PathVariable orderId: Long): PaymentResponse {
        return PaymentResponse.from(paymentService.getPaymentByOrderId(orderId))
    }

    @GetMapping("/{paymentId}")
    @ResponseStatus(HttpStatus.OK)
    fun getPayment(@PathVariable paymentId: Long): PaymentResponse {
        return PaymentResponse.from(paymentService.getPayment(paymentId))
    }

    @PostMapping("/{paymentId}/refund")
    @ResponseStatus(HttpStatus.OK)
    fun refundPayment(
        @UserId memberId: Long,
        @PathVariable paymentId: Long
    ): PaymentResponse {
        return PaymentResponse.from(paymentService.refundPayment(paymentId, memberId))
    }
}
