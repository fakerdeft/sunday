package com.sunday.support.infra.lock

import java.util.concurrent.TimeUnit

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
    val key: String,
    val waitTime: Long = 0,
    val leaseTime: Long = 5,
    val timeUnit: TimeUnit = TimeUnit.SECONDS
)
