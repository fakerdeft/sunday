package com.sunday.order.benchmark.stream

import com.sunday.order.application.StockReservationService
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.ReservationOrigin
import com.sunday.order.repository.OrderReservationRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 요청 ID 를 예약 키로 써서 메시지 재배달에도 같은 예약을 돌려준다. */
@Profile("local")
@Service
class OrderStreamReservationService(
    private val reservationRepository: OrderReservationRepository,
    private val stockReservationService: StockReservationService
) {
    @Transactional
    fun create(requestId: String, memberId: Long, productId: Long, quantity: Int): OrderReservation {
        val reservationKey = ReservationOrigin.STREAM_QUEUE.key(requestId)
        val existing = reservationRepository.findByReservationKey(reservationKey)

        if (existing != null) {

            return existing
        }

        return stockReservationService.reserve(memberId, productId, quantity, reservationKey)
    }
}
