package com.sunday.account.application

import com.sunday.account.domain.DuplicateAccountOperationException
import com.sunday.account.repository.AccountTransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@Testcontainers
class AccountServiceIdempotencyTest {

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("sunday_account_test")
            withUsername("sunday")
            withPassword("sunday123")
            withInitScript("db/schema-only.sql")
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create" }
            registry.add("spring.jpa.properties.hibernate.default_schema") { "account_service" }
        }
    }

    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var transactionRepository: AccountTransactionRepository

    @Test
    fun `concurrent retries with one operation id debit the account once`() {
        val memberId = 101L
        val account = accountService.createAccount(memberId, "idempotency-user")

        accountService.deposit(account.id, BigDecimal("100000"), "seed")
        val start = CountDownLatch(1)
        val success = AtomicInteger(0)
        val failures = AtomicInteger(0)
        val threads = (1..20).map {
            Thread {
                try {
                    start.await()
                    accountService.withdrawForMember(
                        memberId = memberId,
                        amount = BigDecimal("30000"),
                        description = "payment",
                        operationId = "payment:1:charge"
                    )
                    success.incrementAndGet()
                } catch (_: Exception) {
                    failures.incrementAndGet()
                }
            }
        }

        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join() }

        val finalAccount = accountService.getAccountByMemberId(memberId)
        val operation = transactionRepository.findByOperationId("payment:1:charge")

        assertThat(success.get()).isEqualTo(20)
        assertThat(failures.get()).isZero()
        assertThat(finalAccount.balance).isEqualByComparingTo("70000")
        assertThat(operation).isNotNull()
        assertThat(operation!!.amount).isEqualByComparingTo("30000")
    }

    @Test
    fun `reusing an operation id with different amount is rejected`() {
        val memberId = 102L
        val account = accountService.createAccount(memberId, "conflict-user")

        accountService.deposit(account.id, BigDecimal("100000"), "seed")
        accountService.withdrawForMember(
            memberId,
            BigDecimal("10000"),
            "first",
            "payment:2:charge"
        )

        assertThatThrownBy {
            accountService.withdrawForMember(
                memberId,
                BigDecimal("20000"),
                "conflicting retry",
                "payment:2:charge"
            )
        }.isInstanceOf(DuplicateAccountOperationException::class.java)

        assertThat(accountService.getAccountByMemberId(memberId).balance).isEqualByComparingTo("90000")
    }
}
