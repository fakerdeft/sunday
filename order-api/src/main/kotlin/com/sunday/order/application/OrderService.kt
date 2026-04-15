package com.sunday.order.application

import com.sunday.order.domain.*
import com.sunday.order.repository.OrderRepository
import com.sunday.order.repository.ProductRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class OrderService(
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository
) {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    private val self: OrderService get() = applicationContext.getBean(OrderService::class.java)

    private val lock = Any()

    @Transactional(readOnly = true)
    fun getProducts(): List<Product> = productRepository.findAll()

    @Transactional(readOnly = true)
    fun getHotDeals(): List<Product> = productRepository.findHotDeals()

    @Transactional(readOnly = true)
    fun getProduct(productId: Long): Product {
        return productRepository.findById(productId) ?: throw ProductNotFoundException(productId)
    }

    fun createOrderWithSynchronized(memberId: Long, productId: Long, quantity: Int): Order {
        synchronized(lock) {
            return self.createOrderTransactional(memberId, productId, quantity, "synchronized")
        }
    }

    @Transactional
    fun createOrderTransactional(memberId: Long, productId: Long, quantity: Int, prefix: String): Order {
        if (orderRepository.existsPendingOrder(memberId, productId)) throw DuplicatePendingOrderException(memberId, productId)

        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)

        if (product.isHotDeal && !product.isHotDealActive()) throw HotDealNotActiveException(productId)
        if (product.stock < quantity) throw OutOfStockException(productId, quantity, product.stock)

        product.decreaseStock(quantity)
        productRepository.save(product)

        val order = Order.create(memberId, product, quantity, "$prefix:${UUID.randomUUID()}")
        return orderRepository.save(order)
    }

    @Transactional
    fun createOrderWithPessimisticLock(memberId: Long, productId: Long, quantity: Int): Order {
        if (orderRepository.existsPendingOrder(memberId, productId)) throw DuplicatePendingOrderException(memberId, productId)

        val product = productRepository.findByIdWithPessimisticLock(productId) ?: throw ProductNotFoundException(productId)

        if (product.isHotDeal && !product.isHotDealActive()) throw HotDealNotActiveException(productId)
        if (product.stock < quantity) throw OutOfStockException(productId, quantity, product.stock)

        product.decreaseStock(quantity)
        productRepository.save(product)

        val order = Order.create(memberId, product, quantity, "db-lock:${UUID.randomUUID()}")
        return orderRepository.save(order)
    }

    @Transactional
    fun createOrderWithDistributedLock(memberId: Long, productId: Long, quantity: Int): Order {
        if (orderRepository.existsPendingOrder(memberId, productId)) throw DuplicatePendingOrderException(memberId, productId)

        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)

        if (product.isHotDeal && !product.isHotDealActive()) throw HotDealNotActiveException(productId)
        if (product.stock < quantity) throw OutOfStockException(productId, quantity, product.stock)

        product.decreaseStock(quantity)
        productRepository.save(product)

        val order = Order.create(memberId, product, quantity, "distributed-lock:${UUID.randomUUID()}")
        return orderRepository.save(order)
    }

    @Transactional(readOnly = true)
    fun getOrder(orderId: Long): Order {
        return orderRepository.findById(orderId) ?: throw OrderNotFoundException(orderId)
    }

    @Transactional(readOnly = true)
    fun getMyOrders(memberId: Long): List<Order> = orderRepository.findByMemberId(memberId)

    @Transactional
    fun cancelOrder(orderId: Long): Order {
        val order = getOrder(orderId)
        val cancelledOrder = order.markAsCancelled()
        val product = getProduct(order.productId)
        product.increaseStock(order.quantity)
        productRepository.save(product)
        return orderRepository.save(cancelledOrder)
    }

    @Transactional
    fun markOrderAsPaid(orderId: Long): Order {
        val order = getOrder(orderId)
        return orderRepository.save(order.markAsPaid())
    }

    @Transactional
    fun expireOrders(): Int {
        val expiredOrders = orderRepository.findExpiredPendingOrders()
        expiredOrders.forEach { order ->
            try {
                val product = getProduct(order.productId)
                product.increaseStock(order.quantity)
                productRepository.save(product)
                orderRepository.save(order.markAsExpired())
            } catch (e: Exception) {
                // 개별 주문 처리 실패 시 계속 진행
            }
        }
        return expiredOrders.size
    }
}
