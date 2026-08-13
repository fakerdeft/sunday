package com.sunday.account.repository

import com.sunday.account.domain.AccountTransaction
import com.sunday.account.domain.TransactionType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "account_transaction",
    schema = "account_service",
    indexes = [Index(name = "uq_account_transaction_operation", columnList = "operation_id", unique = true)]
)
class AccountTransactionJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    val account: AccountJpaEntity,

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    val transactionType: TransactionType,

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    val balanceAfter: BigDecimal,

    @Column(name = "description", length = 500)
    val description: String?,

    @Column(name = "operation_id", length = 150, unique = true)
    val operationId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun from(domain: AccountTransaction, account: AccountJpaEntity): AccountTransactionJpaEntity =
            AccountTransactionJpaEntity(
                id = domain.id,
                account = account,
                transactionType = domain.transactionType,
                amount = domain.amount,
                balanceAfter = domain.balanceAfter,
                description = domain.description,
                operationId = domain.operationId,
                createdAt = domain.createdAt
            )
    }

    fun toDomain(): AccountTransaction = AccountTransaction(
        id = id,
        accountId = account.id,
        transactionType = transactionType,
        amount = amount,
        balanceAfter = balanceAfter,
        description = description,
        operationId = operationId,
        createdAt = createdAt
    )
}
