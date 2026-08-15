package com.sunday.order.application

import com.sunday.common.admission.AdmissionTokenCodec
import com.sunday.order.domain.NotAdmittedException
import com.sunday.order.domain.OrderReservation
import org.springframework.stereotype.Service

/**
 * 대기열에서 입장 증표를 받은 회원만 주문할 수 있게 한다.
 *
 * 증표는 대기열 서버가 발급하고 여기서는 서명만 검증한다.
 * 두 서버가 저장소를 공유하거나 서로를 호출하지 않는다.
 */
@Service
class AdmittedOrderService(
    private val admissionTokenCodec: AdmissionTokenCodec,
    private val orderService: OrderService
) {
    fun createReservation(
        memberId: Long,
        productId: Long,
        quantity: Int,
        admissionToken: String?
    ): OrderReservation {
        val result = admissionTokenCodec.verify(admissionToken, memberId, productId)

        if (!result.isValid()) {
            throw NotAdmittedException(productId, memberId, result.name)
        }

        return orderService.createAdmittedReservation(memberId, productId, quantity)
    }
}
