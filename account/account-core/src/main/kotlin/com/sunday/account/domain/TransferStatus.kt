package com.sunday.account.domain

/**
 * 송금 상태
 */
enum class TransferStatus {
    /** 송금 처리 중 */
    PENDING,

    /** 송금 완료 */
    COMPLETED,

    /** 송금 실패 */
    FAILED,

    /** 송금 취소됨 */
    REVERSED
}
