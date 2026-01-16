package com.sunday.member.adapter.outbound

import com.sunday.member.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Member JPA Entity (Infrastructure Layer)
 *
 * - 도메인 모델(Member)과 분리된 영속성 모델
 * - JPA 의존성은 infra 계층에만 존재
 */
@Entity
@Table(name = "member", schema = "sunday")
class MemberJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * JPA Entity -> Domain Model 변환
     */
    fun toDomain(): Member {
        return Member(
            id = this.id,
            name = this.name,
            createdAt = this.createdAt
        )
    }

    companion object {
        /**
         * Domain Model -> JPA Entity 변환
         */
        fun fromDomain(member: Member): MemberJpaEntity {
            return MemberJpaEntity(
                id = member.id,
                name = member.name,
                createdAt = member.createdAt
            )
        }
    }
}
