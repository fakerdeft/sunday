package com.sunday.member.repository

import com.sunday.member.domain.Member
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MemberRepository(private val jpaRepository: MemberJpaRepository) {

    fun save(domain: Member): Member =
        jpaRepository.save(MemberJpaEntity.from(domain)).toDomain()

    fun findById(id: Long): Member? =
        jpaRepository.findByIdOrNull(id)?.toDomain()

    fun findAll(): List<Member> =
        jpaRepository.findAll().map { it.toDomain() }

    fun existsById(id: Long): Boolean =
        jpaRepository.existsById(id)
}
