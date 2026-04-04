package com.sunday.member.repository

import com.sunday.member.domain.Member
import org.springframework.stereotype.Repository

@Repository
class MemberRepository(
    private val memberJpaRepository: MemberJpaRepository,
    private val memberMapper: MemberMapper
) {

    fun save(member: Member): Member {
        val entity = memberMapper.toEntity(member)
        return memberMapper.toDomain(memberJpaRepository.save(entity))
    }

    fun findById(id: Long): Member? {
        return memberJpaRepository.findById(id)
            .orElse(null)
            ?.let { memberMapper.toDomain(it) }
    }

    fun findAll(): List<Member> {
        return memberJpaRepository.findAll()
            .map { memberMapper.toDomain(it) }
    }

    fun existsById(id: Long): Boolean {
        return memberJpaRepository.existsById(id)
    }
}
