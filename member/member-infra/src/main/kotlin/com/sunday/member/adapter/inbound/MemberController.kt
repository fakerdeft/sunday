package com.sunday.member.adapter.inbound

import com.sunday.common.auth.UserId
import com.sunday.member.application.MemberService
import com.sunday.member.domain.Member
import org.springframework.web.bind.annotation.*

/**
 * Member REST Controller
 */
@RestController
@RequestMapping("/api/members")
class MemberController(
    private val memberService: MemberService
) {
    @GetMapping("/{id}")
    fun getMember(@PathVariable id: Long): MemberResponse {
        val member = memberService.getMemberById(id)
        return MemberResponse.from(member)
    }

    @GetMapping
    fun getAllMembers(): List<MemberResponse> {
        return memberService.getAllMembers().map { MemberResponse.from(it) }
    }

    @PostMapping
    fun createMember(@RequestBody request: CreateMemberRequest): MemberResponse {
        val member = memberService.createMember(request.name)
        return MemberResponse.from(member)
    }

    @GetMapping("/me")
    fun getMyInfo(@UserId memberId: Long): MemberResponse {
        val member = memberService.getMemberById(memberId)
        return MemberResponse.from(member)
    }
}

/**
 * Member 응답 DTO
 */
data class MemberResponse(
    val id: Long,
    val name: String,
    val createdAt: String
) {
    companion object {
        fun from(member: Member): MemberResponse {
            return MemberResponse(
                id = member.id,
                name = member.name,
                createdAt = member.createdAt.toString()
            )
        }
    }
}

/**
 * Member 생성 요청 DTO
 */
data class CreateMemberRequest(
    val name: String
)
