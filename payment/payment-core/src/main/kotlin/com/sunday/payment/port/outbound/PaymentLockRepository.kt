package com.sunday.payment.port.outbound

/**
 * Payment Lock Repository (Output Port) - Redis 분산 락
 */
interface PaymentLockRepository {
    /**
     * 결제 락 획득
     * @param orderId 주문 ID
     * @param ttlSeconds 락 유효 시간 (초)
     * @return 락 획득 성공 여부
     */
    fun acquireLock(orderId: Long, ttlSeconds: Long): Boolean

    /**
     * 결제 락 해제
     * @param orderId 주문 ID
     */
    fun releaseLock(orderId: Long)

    /**
     * 멱등성 키 등록 (이미 존재하면 false)
     * @param idempotencyKey 멱등성 키
     * @param ttlSeconds TTL (초)
     * @return 등록 성공 여부 (이미 존재하면 false)
     */
    fun registerIdempotencyKey(idempotencyKey: String, ttlSeconds: Long): Boolean

    /**
     * 멱등성 키 존재 여부 확인
     */
    fun existsIdempotencyKey(idempotencyKey: String): Boolean
}
