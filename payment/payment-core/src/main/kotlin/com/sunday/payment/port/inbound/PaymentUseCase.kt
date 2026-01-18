package com.sunday.payment.port.inbound

import com.sunday.payment.domain.Payment

/**
 * Payment Use Case (Input Port)
 */
interface PaymentUseCase {
    /**
     * 결제 처리
     *
     * 1. 분산 락 획득 (동일 주문 중복 결제 방지)
     * 2. 멱등성 키 확인 (같은 요청 중복 방지)
     * 3. 주문 상태 확인 (PENDING, 만료 안됨)
     * 4. 계좌 잔액 차감
     * 5. 주문 상태 변경 (PAID)
     * 6. 결제 기록 저장
     *
     * @param orderId 주문 ID
     * @param memberId 회원 ID (주문자 확인용)
     * @param idempotencyKey 멱등성 키 (클라이언트 생성)
     * @return 결제 정보
     */
    fun processPayment(orderId: Long, memberId: Long, idempotencyKey: String): Payment

    /**
     * 결제 조회
     */
    fun getPayment(paymentId: Long): Payment

    /**
     * 주문 ID로 결제 조회
     */
    fun getPaymentByOrderId(orderId: Long): Payment

    /**
     * 내 결제 내역
     */
    fun getMyPayments(memberId: Long): List<Payment>

    /**
     * 결제 환불
     *
     * 1. 결제 상태 확인 (COMPLETED)
     * 2. 계좌 잔액 복구
     * 3. 주문 상태 변경 (CANCELLED)
     * 4. 재고 복구
     * 5. 결제 상태 변경 (REFUNDED)
     */
    fun refundPayment(paymentId: Long): Payment
}
