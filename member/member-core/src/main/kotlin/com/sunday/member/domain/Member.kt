package com.sunday.member.domain

import com.sunday.member.exception.InvalidMemberNameException
import java.time.LocalDateTime

/**
 * Member 도메인 모델 (순수 비즈니스 로직)
 *
 * 헥사고날 아키텍처의 핵심 도메인 모델
 * - 인프라(JPA, Spring) 의존성 없음
 * - 불변성 보장 (data class)
 */
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
        /**
         * 새로운 Member 생성
         */
        fun create(name: String): Member {
            return Member(
                id = 0L,
                name = name
            )
        }
    }
}
