package com.sunday.payment.application

import com.sunday.payment.client.AccountApiClient
import com.sunday.payment.client.OrderApiClient
import com.sunday.payment.client.ReservationInfo
import com.sunday.payment.domain.Payment
import com.sunday.payment.domain.PaymentStatus
import com.sunday.payment.domain.exception.DuplicatePaymentException
import com.sunday.payment.domain.exception.OrderNotPayableForPaymentException
import com.sunday.payment.domain.exception.PaymentProcessFailedException
import org.apache.logging.log4j.LogManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class PaymentService(
    private val paymentTransactionService: PaymentTransactionService,
    private val accountApiClient: AccountApiClient,
    private val orderApiClient: OrderApiClient
) {
    private val log = LogManager.getLogger(javaClass)

    /**
     * 외부 API 호출은 DB 트랜잭션 밖에서 실행한다. 각 단계는 고정된 작업 키로 재시도하고,
     * 성공 응답을 확인한 뒤 짧은 로컬 트랜잭션으로 다음 상태를 기록한다.
     */
    fun processPayment(reservationId: Long, memberId: Long, idempotencyKey: String): Payment {
        val existing = paymentTransactionService.findByIdempotencyKey(idempotencyKey)

        if (existing != null) {
            validateSameRequest(existing, reservationId, memberId, idempotencyKey)

            return processByStatus(existing)
        }

        val payment = initializePayment(reservationId, memberId, idempotencyKey)

        return processByStatus(payment)
    }

    fun getMyPayments(memberId: Long): List<Payment> = paymentTransactionService.getMyPayments(memberId)

    fun getPaymentByOrderId(reservationId: Long): Payment =
        paymentTransactionService.getPaymentByOrderId(reservationId)

    fun getPayment(paymentId: Long): Payment = paymentTransactionService.getPayment(paymentId)

    fun refundPayment(paymentId: Long, memberId: Long): Payment {
        val payment = paymentTransactionService.startRefund(paymentId, memberId)

        if (payment.status == PaymentStatus.REFUNDED) {

            return payment
        }

        try {
            orderApiClient.cancelOrder(payment.orderId)
            accountApiClient.deposit(
                memberId = payment.memberId,
                amount = payment.amount,
                description = "주문 환불 (예약번호: ${payment.orderId})",
                operationId = refundOperationId(payment.id)
            )
        } catch (e: Exception) {
            log.error("환불 단계가 완료되지 않아 재시도가 필요합니다: paymentId=$paymentId", e)
            throw PaymentProcessFailedException(
                payment.orderId,
                "환불 단계를 완료하지 못했습니다. 같은 환불 요청을 재시도해 주세요"
            )
        }

        return paymentTransactionService.completeRefund(payment.id)
    }

    private fun processByStatus(payment: Payment): Payment {
        return when (payment.status) {
            PaymentStatus.COMPLETED,
            PaymentStatus.REFUNDED,
            PaymentStatus.REFUND_PROCESSING -> payment

            PaymentStatus.FAILED -> throw PaymentProcessFailedException(
                payment.orderId,
                payment.failureReason ?: "결제 처리에 실패했습니다"
            )

            else -> resumePayment(payment)
        }
    }

    private fun initializePayment(reservationId: Long, memberId: Long, idempotencyKey: String): Payment {
        val existing = findExistingPaymentForOrder(reservationId, memberId, idempotencyKey)

        if (existing != null) {

            return existing
        }

        val reservation = orderApiClient.getReservationInfo(reservationId)

        validateReservationOwnerAndAmount(reservation, memberId, expectedAmount = null)

        if (!reservation.canPay()) {
            // A concurrent request may have completed the order after the first DB lookup.
            val concurrent = findExistingPaymentForOrder(reservationId, memberId, idempotencyKey)

            if (concurrent != null) {

                return concurrent
            }

            throw OrderNotPayableForPaymentException(
                reservationId,
                "예약 상태=${reservation.status}, 만료=${reservation.isExpired}"
            )
        }

        return try {
            paymentTransactionService.createProcessingPayment(
                reservationId = reservationId,
                memberId = memberId,
                amount = reservation.totalAmount,
                idempotencyKey = idempotencyKey
            )
        } catch (e: DataIntegrityViolationException) {
            val concurrent = paymentTransactionService.findByIdempotencyKey(idempotencyKey)
                ?: paymentTransactionService.findByOrderId(reservationId)
                ?: throw e
            validateSameRequest(concurrent, reservationId, memberId, idempotencyKey)
            concurrent
        }
    }

    private fun findExistingPaymentForOrder(
        reservationId: Long,
        memberId: Long,
        idempotencyKey: String
    ): Payment? = paymentTransactionService.findByOrderId(reservationId)?.also {
        validateSameRequest(it, reservationId, memberId, idempotencyKey)
    }

    private fun resumePayment(payment: Payment): Payment {
        var current = payment

        if (current.status == PaymentStatus.PROCESSING) {
            current = debitAccountOrRecover(current)
        }

        if (current.status == PaymentStatus.ACCOUNT_DEBITED) {
            current = confirmOrderOrRecover(current)
        }

        if (current.status == PaymentStatus.ORDER_CONFIRMED) {
            current = paymentTransactionService.completePayment(current.id)
        }

        return current
    }

    private fun debitAccountOrRecover(payment: Payment): Payment {
        val reservation = getReservationForRecovery(payment.orderId)

        validateReservationOwnerAndAmount(reservation, payment.memberId, payment.amount)

        val reservationTerminated =
            (reservation.status != "PENDING" && reservation.status != "CONFIRMED") ||
                (reservation.status == "PENDING" && reservation.isExpired)

        if (reservationTerminated) {
            if (!isChargeApplied(payment)) {

                return failAndThrow(payment, "결제 전에 예약이 ${reservation.status} 상태로 종료되었습니다")
            }

            val debited = paymentTransactionService.markAccountDebited(payment.id)

            return recoverDebitedPayment(
                debited,
                "계좌 차감 응답을 기다리는 동안 예약이 ${reservation.status} 상태로 종료되었습니다"
            )
        }

        try {
            accountApiClient.withdraw(
                memberId = payment.memberId,
                amount = payment.amount,
                description = "주문 결제 (예약번호: ${payment.orderId})",
                operationId = chargeOperationId(payment.id)
            )
        } catch (e: Exception) {
            log.error("계좌 차감 결과를 확인할 수 없습니다: paymentId=${payment.id}", e)

            if (!isChargeApplied(payment)) {
                throw PaymentProcessFailedException(
                    payment.orderId,
                    "계좌 차감 결과를 확인하지 못했습니다. 같은 멱등성 키로 재시도해 주세요"
                )
            }
        }

        return paymentTransactionService.markAccountDebited(payment.id)
    }

    private fun confirmOrderOrRecover(payment: Payment): Payment {
        val reservation = getReservationForRecovery(payment.orderId)

        validateReservationOwnerAndAmount(reservation, payment.memberId, payment.amount)

        if (reservation.status == "CONFIRMED") {

            return paymentTransactionService.markOrderConfirmed(payment.id)
        }
        if (!reservation.canPay()) {

            return recoverDebitedPayment(
                payment,
                "주문 확정 전에 예약이 ${reservation.status} 상태로 종료되었습니다"
            )
        }

        try {
            orderApiClient.confirmReservation(payment.orderId)

            return paymentTransactionService.markOrderConfirmed(payment.id)
        } catch (e: Exception) {
            log.error("주문 확정 결과를 확인할 수 없습니다: paymentId=${payment.id}", e)
            val reconciled = runCatching { orderApiClient.getReservationInfo(payment.orderId) }.getOrNull()

            if (reconciled?.status == "CONFIRMED") {

                return paymentTransactionService.markOrderConfirmed(payment.id)
            }
            if (reconciled != null && !reconciled.canPay()) {

                return recoverDebitedPayment(
                    payment,
                    "주문 확정 실패 후 예약 상태=${reconciled.status}, 만료=${reconciled.isExpired}"
                )
            }
            throw PaymentProcessFailedException(
                payment.orderId,
                "주문 확정 결과를 확인하지 못했습니다. 같은 멱등성 키로 재시도해 주세요"
            )
        }
    }

    private fun recoverDebitedPayment(payment: Payment, reason: String): Payment {
        val beforeCancellation = runCatching { orderApiClient.getReservationInfo(payment.orderId) }.getOrNull()
            ?: throw PaymentProcessFailedException(
                payment.orderId,
                "예약의 최종 상태를 확인하지 못해 환급을 보류했습니다. 같은 멱등성 키로 재시도해 주세요"
            )
        if (beforeCancellation.status == "CONFIRMED") {

            return paymentTransactionService.markOrderConfirmed(payment.id)
        }

        if (beforeCancellation.status == "PENDING") {
            runCatching { orderApiClient.cancelReservation(payment.orderId) }
                .onFailure { log.warn("예약 취소 결과를 확인할 수 없습니다: orderId=${payment.orderId}", it) }
        }

        val terminal = runCatching { orderApiClient.getReservationInfo(payment.orderId) }.getOrNull()
            ?: throw PaymentProcessFailedException(
                payment.orderId,
                "예약의 최종 상태를 확인하지 못해 환급을 보류했습니다. 같은 멱등성 키로 재시도해 주세요"
            )
        if (terminal.status == "CONFIRMED") {

            return paymentTransactionService.markOrderConfirmed(payment.id)
        }
        val safelyTerminated = terminal.status == "CANCELLED" ||
            terminal.status == "EXPIRED" ||
            (terminal.status == "PENDING" && terminal.isExpired)

        if (!safelyTerminated) {
            throw PaymentProcessFailedException(
                payment.orderId,
                "예약 종료를 확인하지 못해 환급을 보류했습니다. 같은 멱등성 키로 재시도해 주세요"
            )
        }

        accountApiClient.deposit(
            memberId = payment.memberId,
            amount = payment.amount,
            description = "결제 실패 환급 (예약번호: ${payment.orderId})",
            operationId = chargeReversalOperationId(payment.id)
        )

        return failAndThrow(payment, reason)
    }

    private fun failAndThrow(payment: Payment, reason: String): Nothing {
        val failed = paymentTransactionService.failPayment(payment.id, reason)

        throw PaymentProcessFailedException(failed.orderId, reason)
    }

    private fun getReservationForRecovery(reservationId: Long): ReservationInfo =
        try {
            orderApiClient.getReservationInfo(reservationId)
        } catch (e: Exception) {
            throw PaymentProcessFailedException(
                reservationId,
                "예약 상태를 확인하지 못했습니다. 같은 멱등성 키로 재시도해 주세요"
            )
        }

    private fun validateReservationOwnerAndAmount(
        reservation: ReservationInfo,
        memberId: Long,
        expectedAmount: BigDecimal?
    ) {
        if (reservation.memberId != memberId) {
            throw OrderNotPayableForPaymentException(reservation.reservationId, "요청 회원의 예약이 아닙니다")
        }
        if (expectedAmount != null && reservation.totalAmount.compareTo(expectedAmount) != 0) {
            throw OrderNotPayableForPaymentException(reservation.reservationId, "예약 금액이 결제 요청 시점과 다릅니다")
        }
    }

    private fun validateSameRequest(
        payment: Payment,
        reservationId: Long,
        memberId: Long,
        idempotencyKey: String
    ) {
        if (payment.orderId != reservationId || payment.memberId != memberId || payment.idempotencyKey != idempotencyKey) {
            throw DuplicatePaymentException(idempotencyKey)
        }
    }

    private fun isChargeApplied(payment: Payment): Boolean {
        val operation = try {
            accountApiClient.getOperation(chargeOperationId(payment.id))
        } catch (e: Exception) {
            throw PaymentProcessFailedException(
                payment.orderId,
                "계좌 차감 작업 상태를 확인하지 못했습니다. 같은 멱등성 키로 재시도해 주세요"
            )
        }

        if (!operation.found) {

            return false
        }

        val matchesPayment = operation.memberId == payment.memberId &&
            operation.transactionType == "WITHDRAWAL" &&
            operation.amount?.compareTo(payment.amount) == 0

        if (!matchesPayment) {
            throw DuplicatePaymentException(payment.idempotencyKey)
        }

        return true
    }

    private fun chargeOperationId(paymentId: Long) = "payment:$paymentId:charge"
    private fun chargeReversalOperationId(paymentId: Long) = "payment:$paymentId:charge-reversal"
    private fun refundOperationId(paymentId: Long) = "payment:$paymentId:refund"
}
