package com.sunday.order.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface ProductJpaRepository : JpaRepository<ProductJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.id = :id")
    fun findByIdWithPessimisticLock(id: Long): Optional<ProductJpaEntity>
}
