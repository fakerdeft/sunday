package com.sunday.account.application

import com.sunday.account.domain.Account
import com.sunday.account.domain.AccountTransaction
import com.sunday.account.exception.AccountAlreadyExistsException
import com.sunday.account.exception.AccountNotFoundByMemberException
import com.sunday.account.exception.AccountNotFoundByUserIdException
import com.sunday.account.exception.AccountNotFoundException
import com.sunday.account.exception.ConcurrentModificationException
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
class AccountService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: AccountTransactionRepository
) : AccountUseCase {

    @Transactional(readOnly = true)
    override fun getAccountById(id: Long): Account {
        return accountRepository.findById(id)
            ?: throw AccountNotFoundException(id)
    }

    @Transactional(readOnly = true)
    override fun getAccountByMemberId(memberId: Long): Account {
        return accountRepository.findByMemberId(memberId)
            ?: throw AccountNotFoundByMemberException(memberId)
    }

    @Transactional(readOnly = true)
    override fun getAccountByUserId(userId: String): Account {
        return accountRepository.findByUserId(userId)
            ?: throw AccountNotFoundByUserIdException(userId)
    }

    @Transactional
    override fun deposit(accountId: Long, amount: BigDecimal, description: String?): Account {
        return executeWithOptimisticLock(accountId) {
            val account = getAccountById(accountId)
            val (updatedAccount, transaction) = account.deposit(amount, description)

            transactionRepository.save(transaction)
            accountRepository.save(updatedAccount)
        }
    }

    @Transactional
    override fun withdraw(accountId: Long, amount: BigDecimal, description: String?): Account {
        return executeWithOptimisticLock(accountId) {
            val account = getAccountById(accountId)
            val (updatedAccount, transaction) = account.withdraw(amount, description)

            transactionRepository.save(transaction)
            accountRepository.save(updatedAccount)
        }
    }

    @Transactional(readOnly = true)
    override fun getTransactionHistory(accountId: Long): List<AccountTransaction> {
        getAccountById(accountId)

        return transactionRepository.findByAccountId(accountId)
    }

    @Transactional(readOnly = true)
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
