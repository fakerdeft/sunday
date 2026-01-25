package com.sunday.order.adapter.outbound

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * Product Spring Data JPA Repository
 *
 * 복잡한 쿼리는 ProductQueryRepository 사용
 */
@Repository
interface ProductJpaRepository : JpaRepository<ProductJpaEntity, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.id = :id")
    fun findByIdWithPessimisticLock(id: Long): Optional<ProductJpaEntity>
}
