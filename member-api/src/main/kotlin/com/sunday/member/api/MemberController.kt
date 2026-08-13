package com.sunday.member.api

import com.sunday.common.auth.UserId
import com.sunday.member.application.MemberService
import com.sunday.member.api.dto.CreateMemberRequest
import com.sunday.member.api.dto.MemberResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/members")
class MemberController(
    private val memberService: MemberService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createMember(@Valid @RequestBody request: CreateMemberRequest): MemberResponse {
        return MemberResponse.from(memberService.createMember(request.name))
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getAllMembers(): List<MemberResponse> {
        return memberService.getAllMembers().map { MemberResponse.from(it) }
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun getMyInfo(@UserId memberId: Long): MemberResponse {
        return MemberResponse.from(memberService.getMemberById(memberId))
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getMember(@PathVariable id: Long): MemberResponse {
        return MemberResponse.from(memberService.getMemberById(id))
    }
}
