package com.sunday.account.domain

/**
 * 거래 유형
 */
enum class TransactionType {
    DEPOSIT,      // 충전
    WITHDRAWAL,   // 차감
    TRANSFER_IN,  // 이체 입금 (향후 확장)
    TRANSFER_OUT  // 이체 출금 (향후 확장)
}
