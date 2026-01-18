package com.sunday.account.port.inbound

import com.sunday.account.domain.Transfer
import java.math.BigDecimal

/**
 * Transfer Use Case (Input Port)
 */
interface TransferUseCase {
    /**
     * 송금 처리 (Saga 패턴)
     *
     * 1. 분산 락 획득 (양쪽 계좌, 데드락 방지)
     * 2. 멱등성 키 확인
     * 3. 출금 계좌 잔액 확인 및 차감
     * 4. 입금 계좌에 입금
     * 5. 송금 기록 저장
     *
     * 실패 시 보상 트랜잭션 (출금 롤백)
     *
     * @param senderMemberId 송금자 회원 ID
     * @param receiverMemberId 수취인 회원 ID
     * @param amount 송금액
     * @param idempotencyKey 멱등성 키
     * @param description 송금 메모
     */
    fun transfer(
        senderMemberId: Long,
        receiverMemberId: Long,
        amount: BigDecimal,
        idempotencyKey: String,
        description: String? = null
    ): Transfer

    /**
     * 송금 조회
     */
    fun getTransfer(transferId: Long): Transfer

    /**
     * 보낸 송금 내역
     */
    fun getSentTransfers(memberId: Long): List<Transfer>

    /**
     * 받은 송금 내역
     */
    fun getReceivedTransfers(memberId: Long): List<Transfer>

    /**
     * 송금 취소 (완료된 송금 되돌리기)
     */
    fun reverseTransfer(transferId: Long): Transfer
}
