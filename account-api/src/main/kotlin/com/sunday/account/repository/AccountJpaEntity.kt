package com.sunday.account.repository

import com.sunday.account.domain.Account
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "account", schema = "sunday")
class AccountJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(name = "user_id", nullable = false, unique = true, length = 100)
    val userId: String,

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun from(domain: Account): AccountJpaEntity = AccountJpaEntity(
            id = domain.id,
            memberId = domain.memberId,
            userId = domain.userId,
            balance = domain.balance,
            version = domain.version,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    fun toDomain(): Account = Account(
        id = id,
        memberId = memberId,
        userId = userId,
        balance = balance,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
