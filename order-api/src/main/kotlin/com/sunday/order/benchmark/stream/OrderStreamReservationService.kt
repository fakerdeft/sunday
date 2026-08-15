package com.sunday.order.benchmark.stream

import com.sunday.order.application.StockReservationService
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.ReservationOrigin
import com.sunday.order.repository.OrderReservationRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Redis Streams 접수 큐 워커가 사용하는 예약 생성 경로다.
 *
 * DB 커밋과 Redis 확인 응답 사이에 장애가 나서 메시지가 다시 배달돼도 같은 예약을 돌려주도록
 * 요청 ID를 예약 키로 사용한다.
 */
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
