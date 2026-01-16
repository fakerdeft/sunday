package com.sunday.member.port.outbound

import com.sunday.member.domain.Member

/**
 * Member Repository Port (헥사고날 아키텍처의 Output Port)
 *
 * - Core 모듈에서 인터페이스만 정의
 * - Infra 모듈에서 JPA 등으로 구현
 * - 비즈니스 로직은 이 인터페이스에만 의존
 */
interface MemberRepository {
    fun save(member: Member): Member
    fun findById(id: Long): Member?
    fun findAll(): List<Member>
    fun existsById(id: Long): Boolean
}
