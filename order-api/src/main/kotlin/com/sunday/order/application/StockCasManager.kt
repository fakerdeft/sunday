package com.sunday.order.application

import com.sunday.order.repository.ProductRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class StockCasManager(private val productRepository: ProductRepository) {

    private val stockMap = ConcurrentHashMap<Long, AtomicInteger>()

    @PostConstruct
    fun init() {
        productRepository.findAll().forEach { p ->
            stockMap[p.id] = AtomicInteger(p.stock)
        }
    }

    fun tryDecrement(productId: Long, quantity: Int): Boolean {
        val atomic = stockMap.getOrPut(productId) {
            AtomicInteger(productRepository.findById(productId)?.stock ?: 0)
        }
        while (true) {
            val current = atomic.get()
            if (current < quantity) return false
            if (atomic.compareAndSet(current, current - quantity)) return true
        }
    }

    fun increment(productId: Long, quantity: Int) {
        stockMap.getOrPut(productId) { AtomicInteger(0) }.addAndGet(quantity)
    }

    fun reset(productId: Long, stock: Int) {
        stockMap[productId] = AtomicInteger(stock)
    }
}
