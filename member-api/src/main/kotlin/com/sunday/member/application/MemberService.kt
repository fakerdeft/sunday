package com.sunday.member.application

import com.sunday.member.domain.Member
import com.sunday.member.domain.MemberNotFoundException
import com.sunday.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val memberRepository: MemberRepository
) {

    @Transactional(readOnly = true)
    fun getMemberById(id: Long): Member {
        return memberRepository.findById(id)
            ?: throw MemberNotFoundException(id)
    }

    @Transactional(readOnly = true)
    fun getAllMembers(): List<Member> {
        return memberRepository.findAll()
    }

    @Transactional
    fun createMember(name: String): Member {
        val member = Member.create(name)

        return memberRepository.save(member)
    }
}
