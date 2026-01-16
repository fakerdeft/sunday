package com.sunday.account.port.outbound

import com.sunday.account.domain.Account

/**
 * Account Repository Port (Output Port)
 *
 * - Core 모듈에서 인터페이스만 정의
 * - Infra 모듈에서 JPA로 구현
 */
interface AccountRepository {
    fun save(account: Account): Account
    fun findById(id: Long): Account?
    fun findByMemberId(memberId: Long): Account?
    fun findByUserId(userId: String): Account?
    fun existsByMemberId(memberId: Long): Boolean
    fun existsByUserId(userId: String): Boolean
}
