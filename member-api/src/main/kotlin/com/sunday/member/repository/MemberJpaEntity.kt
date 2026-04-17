package com.sunday.member.repository

import com.sunday.member.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

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
    companion object {
        fun from(domain: Member): MemberJpaEntity = MemberJpaEntity(
            id = domain.id,
            name = domain.name,
            createdAt = domain.createdAt
        )
    }

    fun toDomain(): Member = Member(
        id = id,
        name = name,
        createdAt = createdAt
    )
}
