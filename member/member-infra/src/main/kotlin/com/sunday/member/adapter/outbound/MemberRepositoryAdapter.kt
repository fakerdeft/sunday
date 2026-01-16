package com.sunday.member.adapter.outbound

import com.sunday.member.domain.Member
import com.sunday.member.port.outbound.MemberRepository
import org.springframework.stereotype.Component


/**
 * Member Repository Adapter (헥사고날 아키텍처의 Output Adapter)
 *
 * - Core의 MemberRepository 포트를 JPA로 구현
 * - 도메인 모델 <-> JPA Entity 변환 담당
 */
@Component
class MemberRepositoryAdapter(
    private val jpaRepository: MemberJpaRepository
) : MemberRepository {

    override fun save(member: Member): Member {
        return jpaRepository.save(MemberJpaEntity.fromDomain(member)).toDomain()
    }

    override fun findById(id: Long): Member? {
        return jpaRepository.findById(id)
            .orElse(null)
            .toDomain()
    }

    override fun findAll(): List<Member> {
        return jpaRepository.findAll()
            .map(MemberJpaEntity::toDomain)
            .toList()
    }

    override fun existsById(id: Long): Boolean {
        return jpaRepository.existsById(id)
    }
}
