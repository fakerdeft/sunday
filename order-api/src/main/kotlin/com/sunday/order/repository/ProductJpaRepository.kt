package com.sunday.order.repository

import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<ProductJpaEntity, Long> {
    fun findByIsHotDealTrue(): List<ProductJpaEntity>
}
