package com.sunday.account.adapter.outbound

import com.sunday.account.domain.Account
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Account JPA Entity
 *
 * - 도메인 모델(Account)과 분리된 영속성 모델
 * - @Version으로 낙관적 락 구현
 */
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
    /**
     * JPA Entity -> Domain Model 변환
     */
    fun toDomain(): Account {
        return Account(
            id = this.id,
            memberId = this.memberId,
            userId = this.userId,
            balance = this.balance,
            version = this.version,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    /**
     * Domain Model의 변경사항 반영 (업데이트용)
     */
    fun updateFrom(account: Account) {
        this.balance = account.balance
        this.updatedAt = account.updatedAt
        // version은 JPA가 자동 관리
    }

    companion object {
        /**
         * Domain Model -> JPA Entity 변환
         */
        fun fromDomain(account: Account): AccountJpaEntity {
            return AccountJpaEntity(
                id = account.id,
                memberId = account.memberId,
                userId = account.userId,
                balance = account.balance,
                version = account.version,
                createdAt = account.createdAt,
                updatedAt = account.updatedAt
            )
        }
    }
}
