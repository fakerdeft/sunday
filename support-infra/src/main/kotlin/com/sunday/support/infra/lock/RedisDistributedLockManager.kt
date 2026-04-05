package com.sunday.support.infra.lock

import com.sunday.support.infra.lock.DistributedLockManager
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Redisson 기반 분산 락 구현
 *
 * - Redisson RLock을 이용한 분산 락
 * - 재진입 락 지원
 * - Watchdog으로 자동 연장 지원 (leaseTime이 -1인 경우)
 */
@Component
class RedisDistributedLockManager(
    private val redissonClient: RedissonClient
) : DistributedLockManager {

    companion object {
        private const val LOCK_PREFIX = "lock:"
    }

    override fun tryLock(key: String, waitTime: Long, leaseTime: Long, timeUnit: TimeUnit): Boolean {
        val lock = redissonClient.getLock("$LOCK_PREFIX$key")

        return try {
            lock.tryLock(waitTime, leaseTime, timeUnit)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    override fun unlock(key: String) {
        val lock = redissonClient.getLock("$LOCK_PREFIX$key")

        if (lock.isHeldByCurrentThread) {
            lock.unlock()
        }
    }
}
