package com.sunday.common.lock

import java.util.concurrent.TimeUnit

/**
 * 분산 락을 적용하는 어노테이션
 *
 * 컨트롤러 메서드에 적용하여 동시 요청을 제어합니다.
 * SpEL 표현식을 사용하여 동적으로 락 키를 생성할 수 있습니다.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
    /**
     * 락 키
     * 예: "'order:' + #userId + ':' + #productId"
     */
    val key: String,

    /**
     * 락 획득 대기 시간
     * 0이면 즉시 실패
     */
    val waitTime: Long = 0,

    /**
     * 락 유지 시간
     * 이 시간이 지나면 자동으로 락이 해제됨
     */
    val leaseTime: Long = 5,

    /**
     * 시간 단위
     */
    val timeUnit: TimeUnit = TimeUnit.SECONDS
)
