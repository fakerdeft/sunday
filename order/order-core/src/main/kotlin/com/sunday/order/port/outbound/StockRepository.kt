package com.sunday.order.port.outbound

/**
 * Stock Repository (Output Port) - Redis 재고 관리
 *
 * 실제 구현은 Redis DECR/INCR을 사용한 원자적 연산
 */
interface StockRepository {
    /**
     * 재고 초기화 (DB → Redis 동기화)
     */
    fun initializeStock(productId: Long, quantity: Int)

    /**
     * 현재 재고 수량 조회
     */
    fun getStock(productId: Long): Int

    /**
     * 재고 차감 (원자적 연산)
     * @return 차감 후 남은 수량. 재고 부족 시 null
     */
    fun decreaseStock(productId: Long, quantity: Int): Int?

    /**
     * 재고 복구 (주문 취소/만료 시)
     * @param maxStock 최대 재고량 (초과 시 증가시키지 않음)
     * @return 증가 후 재고량. 최대 재고 초과 시 null 또는 현재 재고 반환
     */
    fun increaseStock(productId: Long, quantity: Int, maxStock: Int? = null): Int

    /**
     * 재고 선점 키 생성 및 저장 (TTL 적용)
     * @return 선점 키
     */
    fun createReservation(productId: Long, memberId: Long, quantity: Int, ttlSeconds: Long): String

    /**
     * 선점 정보 조회
     */
    fun getReservation(reservationKey: String): StockReservation?

    /**
     * 선점 해제
     */
    fun releaseReservation(reservationKey: String): Boolean
}
