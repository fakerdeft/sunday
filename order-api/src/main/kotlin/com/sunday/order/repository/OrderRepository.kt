package com.sunday.order.repository

import com.sunday.order.domain.Order
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class OrderRepository(private val jpaRepository: OrderJpaRepository) {

    fun findByReservationId(reservationId: Long): Order? =
        jpaRepository.findByIdOrNull(reservationId)?.toDomain()

    fun findByMemberId(memberId: Long): List<Order> =
        jpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId).map { it.toDomain() }

    fun existsPaidOrder(memberId: Long, productId: Long): Boolean =
        jpaRepository.existsByMemberIdAndProductId(memberId, productId)

    fun save(domain: Order): Order =
        jpaRepository.save(OrderJpaEntity.from(domain)).toDomain()

    fun deleteAll() = jpaRepository.deleteAll()
    fun deleteByProductId(productId: Long) = jpaRepository.deleteByProductId(productId)
}
