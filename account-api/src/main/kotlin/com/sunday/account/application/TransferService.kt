package com.sunday.account.application

import com.sunday.account.domain.Transfer
import com.sunday.account.domain.AccountNotFoundByMemberException
import com.sunday.account.domain.TransferNotFoundException
import com.sunday.account.domain.TransferToSelfException
import com.sunday.account.repository.AccountRepository
import com.sunday.account.repository.AccountTransactionRepository
import com.sunday.account.repository.TransferRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class TransferService(
    private val transferRepository: TransferRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: AccountTransactionRepository
) {

    @Transactional
    fun transfer(
        senderMemberId: Long,
        receiverMemberId: Long,
        amount: BigDecimal,
        idempotencyKey: String,
        description: String?
    ): Transfer {
        if (senderMemberId == receiverMemberId) throw TransferToSelfException()

        transferRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        val senderAccount = accountRepository.findByMemberId(senderMemberId)
            ?: throw AccountNotFoundByMemberException(senderMemberId)
        val receiverAccount = accountRepository.findByMemberId(receiverMemberId)
            ?: throw AccountNotFoundByMemberException(receiverMemberId)

        val (withdrawnAccount, withdrawTx) = senderAccount.withdraw(amount, "송금 (받는 분: $receiverMemberId)")

        transactionRepository.save(withdrawTx)
        accountRepository.save(withdrawnAccount)

        val (depositedAccount, depositTx) = receiverAccount.deposit(amount, "송금 받음 (보낸 분: $senderMemberId)")

        transactionRepository.save(depositTx)
        accountRepository.save(depositedAccount)

        val transfer = Transfer.create(
            senderAccountId = senderAccount.id,
            senderMemberId = senderMemberId,
            receiverAccountId = receiverAccount.id,
            receiverMemberId = receiverMemberId,
            amount = amount,
            idempotencyKey = idempotencyKey,
            description = description
        ).complete()

        return transferRepository.save(transfer)
    }

    @Transactional(readOnly = true)
    fun getSentTransfers(memberId: Long): List<Transfer> {
        return transferRepository.findBySenderMemberId(memberId)
    }

    @Transactional(readOnly = true)
    fun getReceivedTransfers(memberId: Long): List<Transfer> {
        return transferRepository.findByReceiverMemberId(memberId)
    }

    @Transactional(readOnly = true)
    fun getTransfer(transferId: Long): Transfer {
        return transferRepository.findById(transferId) ?: throw TransferNotFoundException(transferId)
    }

    @Transactional
    fun reverseTransfer(transferId: Long): Transfer {
        var transfer = getTransfer(transferId)

        transfer = transfer.reverse()

        val receiverAccount = accountRepository.findById(transfer.receiverAccountId)!!
        val senderAccount = accountRepository.findById(transfer.senderAccountId)!!

        val (withdrawnReceiver, withdrawTx) = receiverAccount.withdraw(transfer.amount, "송금 취소")

        transactionRepository.save(withdrawTx)
        accountRepository.save(withdrawnReceiver)

        val (depositedSender, depositTx) = senderAccount.deposit(transfer.amount, "송금 취소 환불")

        transactionRepository.save(depositTx)
        accountRepository.save(depositedSender)

        return transferRepository.save(transfer)
    }
}
