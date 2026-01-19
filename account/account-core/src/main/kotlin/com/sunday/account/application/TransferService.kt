package com.sunday.account.application

import com.sunday.account.domain.Transfer
import com.sunday.account.exception.AccountNotFoundByMemberException
import com.sunday.account.exception.TransferNotFoundException
import com.sunday.account.exception.TransferToSelfException
import com.sunday.account.port.inbound.TransferUseCase
import com.sunday.account.port.outbound.AccountRepository
import com.sunday.account.port.outbound.AccountTransactionRepository
import com.sunday.account.port.outbound.TransferRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 송금 서비스 (단순화 버전)
 *
 * 모놀리식 + 단일 DB 환경에서는 @Transactional로 충분
 * MSA 전환 시 Saga 패턴 적용 필요
 */
@Service
class TransferService(
    private val transferRepository: TransferRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: AccountTransactionRepository
) : TransferUseCase {

    @Transactional
    override fun transfer(
        senderMemberId: Long,
        receiverMemberId: Long,
        amount: BigDecimal,
        idempotencyKey: String,
        description: String?
    ): Transfer {
        // 자기 자신에게 송금 방지
        if (senderMemberId == receiverMemberId) {
            throw TransferToSelfException()
        }

        // 멱등성 체크
        val existingTransfer = transferRepository.findByIdempotencyKey(idempotencyKey)
        if (existingTransfer != null) {
            return existingTransfer
        }

        // 계좌 조회
        val senderAccount = accountRepository.findByMemberId(senderMemberId)
            ?: throw AccountNotFoundByMemberException(senderMemberId)
        val receiverAccount = accountRepository.findByMemberId(receiverMemberId)
            ?: throw AccountNotFoundByMemberException(receiverMemberId)

        // 출금
        val (withdrawnAccount, withdrawTx) = senderAccount.withdraw(
            amount = amount,
            description = "송금 (받는 분: $receiverMemberId)"
        )

        transactionRepository.save(withdrawTx)
        accountRepository.save(withdrawnAccount)

        // 입금
        val (depositedAccount, depositTx) = receiverAccount.deposit(
            amount = amount,
            description = "송금 받음 (보낸 분: $senderMemberId)"
        )

        transactionRepository.save(depositTx)
        accountRepository.save(depositedAccount)

        // 송금 기록 저장
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
    override fun getTransfer(transferId: Long): Transfer {
        return transferRepository.findById(transferId)
            ?: throw TransferNotFoundException(transferId)
    }

    @Transactional(readOnly = true)
    override fun getSentTransfers(memberId: Long): List<Transfer> {
        return transferRepository.findBySenderMemberId(memberId)
    }

    @Transactional(readOnly = true)
    override fun getReceivedTransfers(memberId: Long): List<Transfer> {
        return transferRepository.findByReceiverMemberId(memberId)
    }

    @Transactional
    override fun reverseTransfer(transferId: Long): Transfer {
        var transfer = getTransfer(transferId)

        // 취소 처리
        transfer = transfer.reverse()

        // 수취인 → 송금인 역송금
        val receiverAccount = accountRepository.findById(transfer.receiverAccountId)!!
        val senderAccount = accountRepository.findById(transfer.senderAccountId)!!

        // 수취인 출금
        val (withdrawnReceiver, withdrawTx) = receiverAccount.withdraw(
            amount = transfer.amount,
            description = "송금 취소"
        )

        transactionRepository.save(withdrawTx)
        accountRepository.save(withdrawnReceiver)

        // 송금인 입금
        val (depositedSender, depositTx) = senderAccount.deposit(
            amount = transfer.amount,
            description = "송금 취소 환불"
        )

        transactionRepository.save(depositTx)
        accountRepository.save(depositedSender)

        return transferRepository.save(transfer)
    }
}
