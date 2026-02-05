package com.sunday.payment.adapter.outbound

import com.fasterxml.jackson.databind.ObjectMapper
import com.sunday.payment.port.outbound.OutboxPort
import com.sunday.outbox.AggregateType
import com.sunday.outbox.OutboxEvent
import com.sunday.outbox.OutboxEventRepository
import com.sunday.outbox.OutboxEventType
import com.sunday.outbox.payload.PaymentCompletedPayload
import com.sunday.outbox.payload.PaymentRefundedPayload
import org.springframework.stereotype.Component

@Component
class OutboxPortAdapter(
    private val outboxRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) : OutboxPort {

    override fun savePaymentCompletedEvent(
        paymentId: Long,
        orderId: Long,
        memberId: Long,
        amount: String
    ) {
        val payload = objectMapper.writeValueAsString(
            PaymentCompletedPayload(
                paymentId = paymentId,
                orderId = orderId,
                memberId = memberId,
                amount = amount
            )
        )

        outboxRepository.save(
            OutboxEvent.create(
                aggregateType = AggregateType.PAYMENT,
                aggregateId = paymentId,
                eventType = OutboxEventType.PAYMENT_COMPLETED,
                payload = payload
            )
        )
    }

    override fun savePaymentRefundedEvent(
        paymentId: Long,
        orderId: Long,
        memberId: Long,
        amount: String
    ) {
        val payload = objectMapper.writeValueAsString(
            PaymentRefundedPayload(
                paymentId = paymentId,
                orderId = orderId,
                memberId = memberId,
                amount = amount
            )
        )

        outboxRepository.save(
            OutboxEvent.create(
                aggregateType = AggregateType.PAYMENT,
                aggregateId = paymentId,
                eventType = OutboxEventType.PAYMENT_REFUNDED,
                payload = payload
            )
        )
    }
}
