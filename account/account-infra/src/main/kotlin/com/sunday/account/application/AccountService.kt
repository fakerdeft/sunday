package com.sunday.account.application

import com.sunday.account.domain.Account
import com.sunday.account.domain.AccountTransaction
import com.sunday.account.exception.*
import com.sunday.account.port.inbound.AccountUseCase
import com.sunday.account.port.outbound.AccountRepository
import com.sunday.account.port.outbound.AccountTransactionRepository
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Account Application Service
 */
@Service
@Transactional(readOnly = true)
class AccountService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: AccountTransactionRepository
) : AccountUseCase {

    override fun getAccountById(id: Long): Account {
        return accountRepository.findById(id)
            ?: throw AccountNotFoundException(id)
    }

    override fun getAccountByMemberId(memberId: Long): Account {
        return accountRepository.findByMemberId(memberId)
            ?: throw AccountNotFoundByMemberException(memberId)
    }

    override fun getAccountByUserId(userId: String): Account {
        return accountRepository.findByUserId(userId)
            ?: throw AccountNotFoundByUserIdException(userId)
    }

    @Transactional
    override fun deposit(accountId: Long, amount: BigDecimal, description: String?): Account {
        return executeWithOptimisticLock(accountId) {
            val account = getAccountById(accountId)
            val (updatedAccount, transaction) = account.deposit(amount, description)

            val savedAccount = accountRepository.save(updatedAccount)
            transactionRepository.save(transaction.copy(accountId = savedAccount.id))

            savedAccount
        }
    }

    @Transactional
    override fun withdraw(accountId: Long, amount: BigDecimal, description: String?): Account {
        return executeWithOptimisticLock(accountId) {
            val account = getAccountById(accountId)

            if (!account.canWithdraw(amount)) {
                throw InsufficientBalanceException(account.balance, amount)
            }

            val (updatedAccount, transaction) = account.withdraw(amount, description)

            val savedAccount = accountRepository.save(updatedAccount)
            transactionRepository.save(transaction.copy(accountId = savedAccount.id))

            savedAccount
        }
    }

    override fun getTransactionHistory(accountId: Long): List<AccountTransaction> {
        getAccountById(accountId)
        return transactionRepository.findByAccountId(accountId)
    }

    override fun getTransactionHistory(accountId: Long, page: Int, size: Int): List<AccountTransaction> {
        getAccountById(accountId)
        return transactionRepository.findByAccountId(accountId, page, size)
    }

    @Transactional
    override fun createAccount(memberId: Long, userId: String): Account {
        if (accountRepository.existsByMemberId(memberId)) {
            throw AccountAlreadyExistsException(memberId)
        }

        val account = Account.create(memberId, userId)
        return accountRepository.save(account)
    }

    private fun <T> executeWithOptimisticLock(accountId: Long, action: () -> T): T {
        return try {
            action()
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw ConcurrentModificationException(accountId)
        }
    }
}
