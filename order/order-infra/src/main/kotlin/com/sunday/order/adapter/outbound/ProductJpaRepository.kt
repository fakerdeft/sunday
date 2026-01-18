package com.sunday.order.adapter.outbound

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Product Spring Data JPA Repository
 *
 * 복잡한 쿼리는 ProductQueryRepository 사용
 */
@Repository
interface ProductJpaRepository : JpaRepository<ProductJpaEntity, Long>
