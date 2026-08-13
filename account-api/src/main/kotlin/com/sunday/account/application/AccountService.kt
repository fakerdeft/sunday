package com.sunday.account.application

import com.sunday.account.domain.Account
import com.sunday.account.domain.AccountTransaction
import com.sunday.account.domain.AccountAlreadyExistsException
import com.sunday.account.domain.AccountNotFoundByMemberException
import com.sunday.account.domain.AccountNotFoundByUserIdException
import com.sunday.account.domain.AccountNotFoundException
import com.sunday.account.domain.ConcurrentModificationException
import com.sunday.account.domain.DuplicateAccountOperationException
import com.sunday.account.domain.TransactionType
import com.sunday.account.repository.AccountRepository
import com.sunday.account.repository.AccountTransactionRepository
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: AccountTransactionRepository
) {

    @Transactional(readOnly = true)
    fun getAccountById(id: Long): Account {
        return accountRepository.findById(id) ?: throw AccountNotFoundException(id)
    }

    @Transactional(readOnly = true)
    fun getAccountByMemberId(memberId: Long): Account {
        return accountRepository.findByMemberId(memberId) ?: throw AccountNotFoundByMemberException(memberId)
    }

    @Transactional(readOnly = true)
    fun getAccountByUserId(userId: String): Account {
        return accountRepository.findByUserId(userId) ?: throw AccountNotFoundByUserIdException(userId)
    }

    @Transactional
    fun deposit(accountId: Long, amount: BigDecimal, description: String?): Account {
        return executeWithOptimisticLock(accountId) {
            val account = getAccountById(accountId)
            val (updatedAccount, transaction) = account.deposit(amount, description)

            transactionRepository.save(transaction)
            accountRepository.save(updatedAccount)
        }
    }

    @Transactional
    fun depositForMember(
        memberId: Long,
        amount: BigDecimal,
        description: String?,
        operationId: String? = null
    ): Account {
        if (operationId != null) {

            return executeIdempotentOperation(
                memberId = memberId,
                amount = amount,
                description = description,
                operationId = operationId,
                type = TransactionType.DEPOSIT
            )
        }

        val account = accountRepository.findByMemberId(memberId)
            ?: throw AccountNotFoundByMemberException(memberId)

        return executeWithOptimisticLock(account.id) {
            val (updatedAccount, transaction) = account.deposit(amount, description)

            transactionRepository.save(transaction)
            accountRepository.save(updatedAccount)
        }
    }

    @Transactional
    fun withdraw(accountId: Long, amount: BigDecimal, description: String?): Account {
        return executeWithOptimisticLock(accountId) {
            val account = getAccountById(accountId)
            val (updatedAccount, transaction) = account.withdraw(amount, description)

            transactionRepository.save(transaction)
            accountRepository.save(updatedAccount)
        }
    }

    @Transactional
    fun withdrawForMember(
        memberId: Long,
        amount: BigDecimal,
        description: String?,
        operationId: String? = null
    ): Account {
        if (operationId != null) {

            return executeIdempotentOperation(
                memberId = memberId,
                amount = amount,
                description = description,
                operationId = operationId,
                type = TransactionType.WITHDRAWAL
            )
        }

        val account = accountRepository.findByMemberId(memberId)
            ?: throw AccountNotFoundByMemberException(memberId)

        return executeWithOptimisticLock(account.id) {
            val (updatedAccount, transaction) = account.withdraw(amount, description)

            transactionRepository.save(transaction)
            accountRepository.save(updatedAccount)
        }
    }

    @Transactional(readOnly = true)
    fun findOperation(operationId: String): AccountOperationResult? {

        val transaction = transactionRepository.findByOperationId(operationId) ?: return null
        val account = accountRepository.findById(transaction.accountId)
            ?: throw AccountNotFoundException(transaction.accountId)

        return AccountOperationResult(
            operationId = operationId,
            memberId = account.memberId,
            transactionType = transaction.transactionType,
            amount = transaction.amount
        )
    }

    @Transactional(readOnly = true)
    fun getTransactionHistory(accountId: Long): List<AccountTransaction> {
        getAccountById(accountId)

        return transactionRepository.findByAccountId(accountId)
    }

    @Transactional(readOnly = true)
    fun getTransactionHistory(accountId: Long, page: Int, size: Int): List<AccountTransaction> {
        getAccountById(accountId)

        return transactionRepository.findByAccountId(accountId, page, size)
    }

    @Transactional(readOnly = true)
    fun getTransactionHistoryForMember(memberId: Long, page: Int, size: Int): List<AccountTransaction> {
        val account = accountRepository.findByMemberId(memberId)
            ?: throw AccountNotFoundByMemberException(memberId)

        return transactionRepository.findByAccountId(account.id, page, size)
    }

    @Transactional
    fun createAccount(memberId: Long, userId: String): Account {
        if (accountRepository.existsByMemberId(memberId)) throw AccountAlreadyExistsException(memberId)
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

    private fun executeIdempotentOperation(
        memberId: Long,
        amount: BigDecimal,
        description: String?,
        operationId: String,
        type: TransactionType
    ): Account {
        require(operationId.isNotBlank() && operationId.length <= 150) {
            "operationId must contain 1 to 150 characters"
        }

        val account = accountRepository.findByMemberIdForUpdate(memberId)
            ?: throw AccountNotFoundByMemberException(memberId)
        val existing = transactionRepository.findByOperationId(operationId)

        if (existing != null) {
            val sameOperation = existing.accountId == account.id &&
                existing.transactionType == type &&
                existing.amount.compareTo(amount) == 0

            if (!sameOperation) throw DuplicateAccountOperationException(operationId)

            return account
        }

        val (updatedAccount, transaction) = when (type) {
            TransactionType.DEPOSIT -> account.deposit(amount, description, operationId)
            TransactionType.WITHDRAWAL -> account.withdraw(amount, description, operationId)
            else -> error("Unsupported idempotent account operation: $type")
        }

        transactionRepository.save(transaction)

        return accountRepository.save(updatedAccount)
    }
}
