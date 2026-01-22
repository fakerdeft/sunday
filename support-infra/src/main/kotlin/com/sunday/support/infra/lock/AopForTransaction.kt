package com.sunday.support.infra.lock

import org.aspectj.lang.ProceedingJoinPoint
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * AOP에서 트랜잭션을 분리하기 위한 컴포넌트
 *
 * Self-invocation 문제를 해결하기 위해 별도 빈으로 분리
 * REQUIRES_NEW로 새 트랜잭션에서 실행하여 락 해제 전에 커밋 보장
 */
@Component
class AopForTransaction {

    /**
     * 새 트랜잭션에서 JoinPoint 실행
     *
     * 1. 락 획득
     * 2. 새 트랜잭션 시작
     * 3. 비즈니스 로직 실행
     * 4. 트랜잭션 커밋
     * 5. 락 해제
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun proceed(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.proceed()
    }
}
