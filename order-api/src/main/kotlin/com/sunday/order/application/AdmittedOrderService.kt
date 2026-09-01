package com.sunday.order.application

import com.sunday.common.admission.AdmissionTokenCodec
import com.sunday.common.admission.AdmissionTokenResult
import com.sunday.order.domain.NotAdmittedException
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.SingleItemOnlyException
import org.springframework.stereotype.Service

/** 통행증 서명만 검증한다. 게이트를 호출하거나 저장소를 공유하지 않는다. */
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
