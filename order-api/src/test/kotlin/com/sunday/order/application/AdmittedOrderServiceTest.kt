package com.sunday.order.application

import com.sunday.common.admission.AdmissionTokenCodec
import com.sunday.order.domain.NotAdmittedException
import com.sunday.order.domain.OrderReservation
import com.sunday.order.domain.ReservationStatus
import com.sunday.order.domain.SingleItemOnlyException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

class AdmittedOrderServiceTest {

    private val codec = AdmissionTokenCodec("test-admission-secret")
    private val orderService = mockk<OrderService>()
    private val admittedOrderService = AdmittedOrderService(codec, orderService)

    private val memberId = 1L
    private val productId = 10L

    private fun reservation() = OrderReservation(
        id = 1L,
        memberId = memberId,
        productId = productId,
        productName = "테스트 상품",
        quantity = 1,
        unitPrice = BigDecimal("10000"),
        totalAmount = BigDecimal("10000"),
        status = ReservationStatus.PENDING,
        reservationKey = "admitted:test",
        expireAt = LocalDateTime.now().plusMinutes(10)
    )

    @Test
    fun `유효한 토큰이 있으면 주문을 생성한다`() {
        val token = codec.issue(memberId, productId, Instant.now().plusSeconds(60))
        every { orderService.createAdmittedReservation(memberId, productId, any()) } returns reservation()

        val created = admittedOrderService.createReservation(memberId, productId, 1, token)

        assertThat(created.memberId).isEqualTo(memberId)
        verify(exactly = 1) { orderService.createAdmittedReservation(memberId, productId, any()) }
    }

    @Test
    fun `토큰이 없으면 주문 로직에 들어가지 않는다`() {
        assertThatThrownBy { admittedOrderService.createReservation(memberId, productId, 1, null) }
            .isInstanceOf(NotAdmittedException::class.java)

        verify(exactly = 0) { orderService.createAdmittedReservation(any(), any(), any()) }
    }

    @Test
    fun `다른 비밀키로 위조한 토큰은 거절한다`() {
        val forged = AdmissionTokenCodec("another-secret")
            .issue(memberId, productId, Instant.now().plusSeconds(60))

        assertThatThrownBy { admittedOrderService.createReservation(memberId, productId, 1, forged) }
            .isInstanceOf(NotAdmittedException::class.java)

        verify(exactly = 0) { orderService.createAdmittedReservation(any(), any(), any()) }
    }

    @Test
    fun `다른 회원의 토큰으로는 주문할 수 없다`() {
        val othersToken = codec.issue(999L, productId, Instant.now().plusSeconds(60))

        assertThatThrownBy { admittedOrderService.createReservation(memberId, productId, 1, othersToken) }
            .isInstanceOf(NotAdmittedException::class.java)

        verify(exactly = 0) { orderService.createAdmittedReservation(any(), any(), any()) }
    }

    @Test
    fun `다른 상품의 토큰으로는 주문할 수 없다`() {
        val otherProductToken = codec.issue(memberId, 99L, Instant.now().plusSeconds(60))

        assertThatThrownBy { admittedOrderService.createReservation(memberId, productId, 1, otherProductToken) }
            .isInstanceOf(NotAdmittedException::class.java)

        verify(exactly = 0) { orderService.createAdmittedReservation(any(), any(), any()) }
    }

    @Test
    fun `수량이 1이 아니면 주문 로직에 들어가지 않는다`() {
        val token = codec.issue(memberId, productId, Instant.now().plusSeconds(60))

        assertThatThrownBy { admittedOrderService.createReservation(memberId, productId, 2, token) }
            .isInstanceOf(SingleItemOnlyException::class.java)

        verify(exactly = 0) { orderService.createAdmittedReservation(any(), any(), any()) }
    }

    @Test
    fun `토큰 검증이 수량 검증보다 먼저다`() {
        assertThatThrownBy { admittedOrderService.createReservation(memberId, productId, 5, null) }
            .isInstanceOf(NotAdmittedException::class.java)

        verify(exactly = 0) { orderService.createAdmittedReservation(any(), any(), any()) }
    }

    @Test
    fun `유효 시간이 지난 토큰은 거절한다`() {
        val expired = codec.issue(memberId, productId, Instant.now().minusSeconds(1))

        assertThatThrownBy { admittedOrderService.createReservation(memberId, productId, 1, expired) }
            .isInstanceOf(NotAdmittedException::class.java)

        verify(exactly = 0) { orderService.createAdmittedReservation(any(), any(), any()) }
    }
}
