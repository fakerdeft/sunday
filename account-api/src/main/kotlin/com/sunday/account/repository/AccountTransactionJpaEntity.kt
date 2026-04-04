package com.sunday.account.repository

import com.sunday.account.domain.TransactionType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

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
)
