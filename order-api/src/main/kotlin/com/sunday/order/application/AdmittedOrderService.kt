package com.sunday.order.application

import com.sunday.common.admission.AdmissionTokenCodec
import com.sunday.common.admission.AdmissionTokenResult
import com.sunday.order.domain.NotAdmittedException
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.SingleItemOnlyException
import org.springframework.stereotype.Service

/**
 * 게이트에서 통행증을 받은 회원만 주문할 수 있게 한다.
 *
 * 통행증은 게이트 서버가 발급하고 여기서는 서명만 검증한다.
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
        if (admissionToken.isNullOrBlank()) {
            throw NotAdmittedException(productId, memberId, AdmissionTokenResult.MISSING.name)
        }

        val result = admissionTokenCodec.verify(admissionToken, memberId, productId)

        if (!result.isValid()) {
            throw NotAdmittedException(productId, memberId, result.name)
        }

        if (quantity != OrderService.ORDER_QUANTITY) {
            throw SingleItemOnlyException(productId, quantity)
        }

        return orderService.createAdmittedReservation(
            memberId = memberId,
            productId = productId,
            tokenFingerprint = AdmissionTokenCodec.fingerprint(admissionToken)
        )
    }
}
