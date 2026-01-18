package com.sunday.account.adapter.outbound

import com.sunday.account.domain.AccountTransaction
import com.sunday.account.domain.TransactionType
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * AccountTransaction JPA Entity
 */
@Entity
@Table(name = "account_transaction", schema = "sunday")
class AccountTransactionJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, insertable = false, updatable = false)
    val account: AccountJpaEntity? = null,

    @Column(name = "account_id", nullable = false)
    val accountId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    val transactionType: TransactionType,

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    val balanceAfter: BigDecimal,

    @Column(name = "description", length = 500)
    val description: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun toDomain(): AccountTransaction {
        return AccountTransaction(
            id = this.id,
            accountId = this.accountId,
            transactionType = this.transactionType,
            amount = this.amount,
            balanceAfter = this.balanceAfter,
            description = this.description,
            createdAt = this.createdAt
        )
    }

    companion object {
        fun fromDomain(transaction: AccountTransaction): AccountTransactionJpaEntity {
            return AccountTransactionJpaEntity(
                id = transaction.id,
                accountId = transaction.accountId,
                transactionType = transaction.transactionType,
                amount = transaction.amount,
                balanceAfter = transaction.balanceAfter,
                description = transaction.description,
                createdAt = transaction.createdAt
            )
        }
    }
}
