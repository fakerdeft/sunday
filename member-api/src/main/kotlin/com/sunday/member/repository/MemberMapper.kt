package com.sunday.member.repository

import com.sunday.member.domain.Member
import org.springframework.stereotype.Component

@Component
class MemberMapper {

    fun toDomain(entity: MemberJpaEntity): Member {
        return Member(
            id = entity.id,
            name = entity.name,
            createdAt = entity.createdAt
        )
    }

    fun toEntity(domain: Member): MemberJpaEntity {
        return MemberJpaEntity(
            id = domain.id,
            name = domain.name,
            createdAt = domain.createdAt
        )
    }
}
