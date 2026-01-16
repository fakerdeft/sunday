package com.sunday.member.adapter.outbound

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Member Spring Data JPA Repository
 */
@Repository
interface MemberJpaRepository : JpaRepository<MemberJpaEntity, Long>
