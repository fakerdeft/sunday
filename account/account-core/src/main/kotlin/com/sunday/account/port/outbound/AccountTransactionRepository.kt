package com.sunday.account.port.outbound

import com.sunday.account.domain.AccountTransaction

/**
 * AccountTransaction Repository Port (Output Port)
 */
interface AccountTransactionRepository {
    fun save(transaction: AccountTransaction): AccountTransaction
    fun findByAccountId(accountId: Long): List<AccountTransaction>
    fun findByAccountId(accountId: Long, page: Int, size: Int): List<AccountTransaction>
}
