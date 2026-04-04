package com.sunday.member.repository

import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<MemberJpaEntity, Long>
