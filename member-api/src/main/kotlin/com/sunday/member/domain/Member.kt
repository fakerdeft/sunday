package com.sunday.member.domain

import java.time.LocalDateTime

data class Member(
    val id: Long,
    val name: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        if (name.isBlank()) {
            throw InvalidMemberNameException()
        }
    }

    companion object {
        fun create(name: String): Member {
            return Member(id = 0L, name = name)
        }
    }
}
