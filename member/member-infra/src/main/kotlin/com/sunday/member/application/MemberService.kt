package com.sunday.member.application

import com.sunday.member.domain.Member
import com.sunday.member.exception.MemberNotFoundException
import com.sunday.member.port.inbound.MemberUseCase
import com.sunday.member.port.outbound.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Member Application Service
 */
@Service
class MemberService(
    private val memberRepository: MemberRepository
) : MemberUseCase {

    @Transactional(readOnly = true)
    override fun getMemberById(id: Long): Member {
        return memberRepository.findById(id)
            ?: throw MemberNotFoundException(id)
    }

    @Transactional(readOnly = true)
    override fun getAllMembers(): List<Member> {
        return memberRepository.findAll()
    }

    @Transactional(readOnly = true)
    override fun existsMember(id: Long): Boolean {
        return memberRepository.existsById(id)
    }

    @Transactional
    override fun createMember(name: String): Member {
        val member = Member.create(name)

        return memberRepository.save(member)
    }
}
