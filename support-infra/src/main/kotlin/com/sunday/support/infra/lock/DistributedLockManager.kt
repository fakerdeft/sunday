package com.sunday.support.infra.lock

import java.util.concurrent.TimeUnit

interface DistributedLockManager {
    fun tryLock(key: String, waitTime: Long, leaseTime: Long, timeUnit: TimeUnit): Boolean
    fun unlock(key: String)
}
