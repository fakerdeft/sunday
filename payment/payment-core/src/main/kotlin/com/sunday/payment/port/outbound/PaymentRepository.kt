package com.sunday.payment.port.outbound

import com.sunday.payment.domain.Payment

/**
 * Payment Repository (Output Port)
 */
interface PaymentRepository {
    fun findById(id: Long): Payment?
    fun findByOrderId(orderId: Long): Payment?
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
    fun findByMemberId(memberId: Long): List<Payment>
    fun save(payment: Payment): Payment
}
