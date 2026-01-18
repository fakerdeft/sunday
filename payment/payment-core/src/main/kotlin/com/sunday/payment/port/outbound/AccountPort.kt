package com.sunday.payment.port.outbound

import java.math.BigDecimal

/**
 * Account 도메인 연동 포트
 */
interface AccountPort {
    /**
     * 계좌 잔액 조회
     */
    fun getBalance(memberId: Long): BigDecimal

    /**
     * 계좌에서 출금 (결제)
     */
    fun withdraw(memberId: Long, amount: BigDecimal, description: String)

    /**
     * 계좌에 입금 (환불)
     */
    fun deposit(memberId: Long, amount: BigDecimal, description: String)
}
