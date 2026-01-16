package com.sunday.member.domain

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
        require(name.isNotBlank()) { "Member name cannot be blank" }
    }

    companion object {
        /**
         * 새로운 Member 생성 (ID는 DB에서 자동 생성)
         */
        fun create(name: String): Member {
            return Member(
                id = 0L, // ID는 persistence layer에서 할당됨
                name = name
            )
        }
    }
}
