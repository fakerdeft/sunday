package com.sunday.support.infra.lock

import com.sunday.common.lock.DistributedLock
import com.sunday.common.lock.DistributedLockManager
import com.sunday.common.lock.LockAcquisitionException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

/**
 * 분산 락 AOP Aspect
 *
 * @DistributedLock 어노테이션이 적용된 메서드에 분산 락을 적용합니다.
 * SpEL 표현식을 사용하여 동적으로 락 키를 생성합니다.
 *
 * 흐름:
 * 1. 락 획득
 * 2. 새 트랜잭션에서 비즈니스 로직 실행 (REQUIRES_NEW)
 * 3. 트랜잭션 커밋
 * 4. 락 해제
 */
@Aspect
@Component
class DistributedLockAspect(
    private val lockManager: DistributedLockManager,
    private val aopForTransaction: AopForTransaction,
    private val spelParser: CustomSpringELParser
) {
    @Around("@annotation(distributedLock)")
    fun around(joinPoint: ProceedingJoinPoint, distributedLock: DistributedLock): Any? {
        val lockKey = spelParser.parseKey(joinPoint, distributedLock.key)

        val acquired = lockManager.tryLock(
            key = lockKey,
            waitTime = distributedLock.waitTime,
            leaseTime = distributedLock.leaseTime,
            timeUnit = distributedLock.timeUnit
        )

        if (!acquired) {
            throw LockAcquisitionException(lockKey)
        }

        try {
            return aopForTransaction.proceed(joinPoint)
        } finally {
            lockManager.unlock(lockKey)
        }
    }
}
