package com.sunday.member.adapter.inbound.dto

import com.sunday.member.domain.Member

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

data class CreateMemberRequest(
    val name: String
)
