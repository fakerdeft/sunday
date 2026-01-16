package com.sunday.member.port.inbound

import com.sunday.member.domain.Member

/**
 * Member Use Case (헥사고날 아키텍처의 Input Port)
 *
 * - Application Layer가 구현해야 할 비즈니스 로직 인터페이스
 * - 외부(Controller)에서 호출하는 진입점
 */
interface MemberUseCase {
    /**
     * 회원 ID로 조회
     */
    fun getMemberById(id: Long): Member

    /**
     * 전체 회원 조회
     */
    fun getAllMembers(): List<Member>

    /**
     * 회원 존재 여부 확인
     */
    fun existsMember(id: Long): Boolean

    /**
     * 새로운 회원 생성
     */
    fun createMember(name: String): Member
}
