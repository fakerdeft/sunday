package com.sunday.payment.adapter.inbound

import com.sunday.payment.adapter.inbound.dto.PaymentResponse
import com.sunday.payment.adapter.inbound.dto.ProcessPaymentRequest
import com.sunday.payment.application.PaymentService
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
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService
) {
    /**
     * 결제 처리
     *
     * - 분산 락으로 동일 주문 중복 결제 방지
     * - 멱등성 키로 같은 요청 중복 처리 방지
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun processPayment(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: ProcessPaymentRequest
    ): PaymentResponse {
        val memberId = userId.toLong()
        val payment = paymentService.processPayment(
            orderId = request.orderId,
            memberId = memberId,
            idempotencyKey = request.idempotencyKey
        )

        return PaymentResponse.from(payment)
    }

    @GetMapping("/{paymentId}")
    @ResponseStatus(HttpStatus.OK)
    fun getPayment(@PathVariable paymentId: Long): PaymentResponse {
        val payment = paymentService.getPayment(paymentId)

        return PaymentResponse.from(payment)
    }

    @GetMapping("/order/{orderId}")
    @ResponseStatus(HttpStatus.OK)
    fun getPaymentByOrderId(@PathVariable orderId: Long): PaymentResponse {
        val payment = paymentService.getPaymentByOrderId(orderId)

        return PaymentResponse.from(payment)
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun getMyPayments(@RequestHeader("X-USER-ID") userId: String): List<PaymentResponse> {
        val memberId = userId.toLong()
        val payments = paymentService.getMyPayments(memberId)

        return payments.map { PaymentResponse.from(it) }
    }

    @PostMapping("/{paymentId}/refund")
    @ResponseStatus(HttpStatus.OK)
    fun refundPayment(@PathVariable paymentId: Long): PaymentResponse {
        val payment = paymentService.refundPayment(paymentId)

        return PaymentResponse.from(payment)
    }
}
