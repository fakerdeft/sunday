package com.sunday.account.application

import com.sunday.account.domain.Account
import com.sunday.account.domain.AccountTransaction
import com.sunday.account.domain.TransactionType
import com.sunday.account.exception.AccountAlreadyExistsException
import com.sunday.account.exception.AccountNotFoundException
import com.sunday.account.port.outbound.AccountRepository
import com.sunday.account.port.outbound.AccountTransactionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class AccountServiceTest : DescribeSpec({

    val accountRepository = mockk<AccountRepository>()
    val transactionRepository = mockk<AccountTransactionRepository>(relaxed = true)
    val accountService = AccountService(accountRepository, transactionRepository)

    describe("getAccountById") {
        context("존재하는 계좌 ID로 조회하면") {
            it("계좌 정보를 반환한다") {
                val account = Account(
                    id = 1L,
                    memberId = 100L,
                    userId = "user123",
                    balance = BigDecimal("10000"),
                    version = 0L
                )
                every { accountRepository.findById(1L) } returns account

                val result = accountService.getAccountById(1L)

                result.id shouldBe 1L
                result.balance shouldBe BigDecimal("10000")
            }
        }

        context("존재하지 않는 계좌 ID로 조회하면") {
            it("AccountNotFoundException이 발생한다") {
                every { accountRepository.findById(999L) } returns null

                shouldThrow<AccountNotFoundException> {
                    accountService.getAccountById(999L)
                }
            }
        }
    }

    describe("createAccount") {
        context("신규 회원으로 계좌 생성하면") {
            it("잔액 0원의 새 계좌가 생성된다") {
                every { accountRepository.existsByMemberId(100L) } returns false
                every { accountRepository.save(any()) } answers {
                    firstArg<Account>().copy(id = 1L)
                }

                val result = accountService.createAccount(100L, "user123")

                result.id shouldBe 1L
                result.memberId shouldBe 100L
                result.balance shouldBe BigDecimal.ZERO
            }
        }

        context("이미 계좌가 있는 회원으로 생성하면") {
            it("AccountAlreadyExistsException이 발생한다") {
                every { accountRepository.existsByMemberId(100L) } returns true

                shouldThrow<AccountAlreadyExistsException> {
                    accountService.createAccount(100L, "user123")
                }
            }
        }
    }

    describe("deposit") {
        context("유효한 금액을 충전하면") {
            it("잔액이 증가하고 거래 내역이 저장된다") {
                val account = Account(
                    id = 1L,
                    memberId = 100L,
                    userId = "user123",
                    balance = BigDecimal("10000"),
                    version = 0L
                )
                every { accountRepository.findById(1L) } returns account
                every { accountRepository.save(any()) } answers { firstArg() }

                val result = accountService.deposit(1L, BigDecimal("5000"), "테스트 충전")

                result.balance shouldBe BigDecimal("15000")
                verify { transactionRepository.save(match { it.transactionType == TransactionType.DEPOSIT }) }
            }
        }
    }

    describe("withdraw") {
        context("잔액 내에서 출금하면") {
            it("잔액이 감소하고 거래 내역이 저장된다") {
                val account = Account(
                    id = 1L,
                    memberId = 100L,
                    userId = "user123",
                    balance = BigDecimal("10000"),
                    version = 0L
                )
                every { accountRepository.findById(1L) } returns account
                every { accountRepository.save(any()) } answers { firstArg() }

                val result = accountService.withdraw(1L, BigDecimal("3000"), "테스트 출금")

                result.balance shouldBe BigDecimal("7000")
                verify { transactionRepository.save(match { it.transactionType == TransactionType.WITHDRAWAL }) }
            }
        }
    }

    describe("getTransactionHistory") {
        context("계좌의 거래 내역을 조회하면") {
            it("거래 내역 목록을 반환한다") {
                val account = Account(
                    id = 1L,
                    memberId = 100L,
                    userId = "user123",
                    balance = BigDecimal("10000"),
                    version = 0L
                )
                val transactions = listOf(
                    AccountTransaction(
                        id = 1L,
                        accountId = 1L,
                        transactionType = TransactionType.DEPOSIT,
                        amount = BigDecimal("5000"),
                        balanceAfter = BigDecimal("5000"),
                        description = "충전"
                    ),
                    AccountTransaction(
                        id = 2L,
                        accountId = 1L,
                        transactionType = TransactionType.WITHDRAWAL,
                        amount = BigDecimal("1000"),
                        balanceAfter = BigDecimal("4000"),
                        description = "출금"
                    )
                )
                every { accountRepository.findById(1L) } returns account
                every { transactionRepository.findByAccountId(1L) } returns transactions

                val result = accountService.getTransactionHistory(1L)

                result.size shouldBe 2
                result[0].transactionType shouldBe TransactionType.DEPOSIT
                result[1].transactionType shouldBe TransactionType.WITHDRAWAL
            }
        }
    }
})
