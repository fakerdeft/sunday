package com.sunday.account.application

import com.sunday.account.domain.Account
import com.sunday.account.domain.Transfer
import com.sunday.account.domain.TransferStatus
import com.sunday.account.exception.AccountNotFoundByMemberException
import com.sunday.account.exception.TransferNotFoundException
import com.sunday.account.exception.TransferToSelfException
import com.sunday.account.port.outbound.AccountRepository
import com.sunday.account.port.outbound.AccountTransactionRepository
import com.sunday.account.port.outbound.TransferRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class TransferServiceTest : DescribeSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val transferRepository = mockk<TransferRepository>()
    val accountRepository = mockk<AccountRepository>()
    val transactionRepository = mockk<AccountTransactionRepository>(relaxed = true)
    val transferService = TransferService(transferRepository, accountRepository, transactionRepository)

    describe("transfer") {
        context("유효한 송금 요청이면") {
            it("송금이 완료되고 Transfer가 반환된다") {
                val senderAccount = Account(
                    id = 1L,
                    memberId = 100L,
                    userId = "sender",
                    balance = BigDecimal("10000"),
                    version = 0L
                )
                val receiverAccount = Account(
                    id = 2L,
                    memberId = 200L,
                    userId = "receiver",
                    balance = BigDecimal("5000"),
                    version = 0L
                )

                every { transferRepository.findByIdempotencyKey("key-123") } returns null
                every { accountRepository.findByMemberId(100L) } returns senderAccount
                every { accountRepository.findByMemberId(200L) } returns receiverAccount
                every { accountRepository.save(any()) } answers { firstArg() }
                every { transferRepository.save(any()) } answers {
                    firstArg<Transfer>().copy(id = 1L)
                }

                val result = transferService.transfer(
                    senderMemberId = 100L,
                    receiverMemberId = 200L,
                    amount = BigDecimal("3000"),
                    idempotencyKey = "key-123",
                    description = "테스트 송금"
                )

                result.status shouldBe TransferStatus.COMPLETED
                result.amount shouldBe BigDecimal("3000")
            }
        }

        context("자기 자신에게 송금하면") {
            it("TransferToSelfException이 발생한다") {
                shouldThrow<TransferToSelfException> {
                    transferService.transfer(
                        senderMemberId = 100L,
                        receiverMemberId = 100L,
                        amount = BigDecimal("3000"),
                        idempotencyKey = "key-123"
                    )
                }
            }
        }

        context("이미 처리된 멱등성 키로 요청하면") {
            it("기존 Transfer를 반환한다") {
                val existingTransfer = Transfer(
                    id = 1L,
                    senderAccountId = 1L,
                    senderMemberId = 100L,
                    receiverAccountId = 2L,
                    receiverMemberId = 200L,
                    amount = BigDecimal("3000"),
                    status = TransferStatus.COMPLETED,
                    idempotencyKey = "key-123",
                    description = "기존 송금"
                )
                every { transferRepository.findByIdempotencyKey("key-123") } returns existingTransfer

                val result = transferService.transfer(
                    senderMemberId = 100L,
                    receiverMemberId = 200L,
                    amount = BigDecimal("3000"),
                    idempotencyKey = "key-123"
                )

                result.id shouldBe 1L
                result.status shouldBe TransferStatus.COMPLETED
            }
        }

        context("보내는 회원의 계좌가 없으면") {
            it("AccountNotFoundByMemberException이 발생한다") {
                every { transferRepository.findByIdempotencyKey("key-123") } returns null
                every { accountRepository.findByMemberId(100L) } returns null

                shouldThrow<AccountNotFoundByMemberException> {
                    transferService.transfer(
                        senderMemberId = 100L,
                        receiverMemberId = 200L,
                        amount = BigDecimal("3000"),
                        idempotencyKey = "key-123"
                    )
                }
            }
        }
    }

    describe("getTransfer") {
        context("존재하는 송금 ID로 조회하면") {
            it("Transfer를 반환한다") {
                val transfer = Transfer(
                    id = 1L,
                    senderAccountId = 1L,
                    senderMemberId = 100L,
                    receiverAccountId = 2L,
                    receiverMemberId = 200L,
                    amount = BigDecimal("3000"),
                    status = TransferStatus.COMPLETED,
                    idempotencyKey = "key-123",
                    description = null
                )
                every { transferRepository.findById(1L) } returns transfer

                val result = transferService.getTransfer(1L)

                result.id shouldBe 1L
                result.amount shouldBe BigDecimal("3000")
            }
        }

        context("존재하지 않는 송금 ID로 조회하면") {
            it("TransferNotFoundException이 발생한다") {
                every { transferRepository.findById(999L) } returns null

                shouldThrow<TransferNotFoundException> {
                    transferService.getTransfer(999L)
                }
            }
        }
    }

    describe("reverseTransfer") {
        context("완료된 송금을 취소하면") {
            it("REVERSED 상태가 되고 잔액이 복구된다") {
                val transfer = Transfer(
                    id = 1L,
                    senderAccountId = 1L,
                    senderMemberId = 100L,
                    receiverAccountId = 2L,
                    receiverMemberId = 200L,
                    amount = BigDecimal("3000"),
                    status = TransferStatus.COMPLETED,
                    idempotencyKey = "key-123",
                    description = null
                )
                val senderAccount = Account(
                    id = 1L,
                    memberId = 100L,
                    userId = "sender",
                    balance = BigDecimal("7000"),
                    version = 0L
                )
                val receiverAccount = Account(
                    id = 2L,
                    memberId = 200L,
                    userId = "receiver",
                    balance = BigDecimal("8000"),
                    version = 0L
                )

                every { transferRepository.findById(1L) } returns transfer
                every { accountRepository.findById(1L) } returns senderAccount
                every { accountRepository.findById(2L) } returns receiverAccount
                every { accountRepository.save(any()) } answers { firstArg() }
                every { transferRepository.save(any()) } answers { firstArg() }

                val result = transferService.reverseTransfer(1L)

                result.status shouldBe TransferStatus.REVERSED
                verify(exactly = 2) { accountRepository.save(any()) }
            }
        }
    }
})
