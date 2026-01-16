package com.sunday.member.application

import com.sunday.member.domain.Member
import com.sunday.member.port.inbound.MemberUseCase
import com.sunday.member.port.outbound.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Member Application Service
 */
@Service
@Transactional(readOnly = true)
class MemberService(
    private val memberRepository: MemberRepository
) : MemberUseCase {

    override fun getMemberById(id: Long): Member {
        return memberRepository.findById(id)
            ?: throw IllegalArgumentException("Member not found: $id")
    }

    override fun getAllMembers(): List<Member> {
        return memberRepository.findAll()
    }

    override fun existsMember(id: Long): Boolean {
        return memberRepository.existsById(id)
    }

    @Transactional
    override fun createMember(name: String): Member {
        val member = Member.create(name)
        return memberRepository.save(member)
    }
}
