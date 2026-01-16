package com.sunday.account.port.inbound

import com.sunday.account.domain.Account
import com.sunday.account.domain.AccountTransaction
import java.math.BigDecimal

/**
 * Account Use Case (Input Port)
 *
 * - Application Layer가 구현해야 할 비즈니스 로직 인터페이스
 * - 외부(Controller)에서 호출하는 진입점
 */
interface AccountUseCase {
    /**
     * 계좌 ID로 조회
     */
    fun getAccountById(id: Long): Account

    /**
     * 회원 ID로 계좌 조회
     */
    fun getAccountByMemberId(memberId: Long): Account

    /**
     * 사용자 ID(문자열)로 계좌 조회
     */
    fun getAccountByUserId(userId: String): Account

    /**
     * 잔액 충전
     * @return 갱신된 계좌 정보
     */
    fun deposit(accountId: Long, amount: BigDecimal, description: String? = null): Account

    /**
     * 잔액 차감
     * @return 갱신된 계좌 정보
     */
    fun withdraw(accountId: Long, amount: BigDecimal, description: String? = null): Account

    /**
     * 거래 이력 조회
     */
    fun getTransactionHistory(accountId: Long): List<AccountTransaction>

    /**
     * 거래 이력 조회 (페이징)
     */
    fun getTransactionHistory(accountId: Long, page: Int, size: Int): List<AccountTransaction>

    /**
     * 새로운 계좌 생성
     */
    fun createAccount(memberId: Long, userId: String): Account
}
