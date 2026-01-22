package com.sunday.common.lock

import java.util.concurrent.TimeUnit

/**
 * 분산 락 관리 인터페이스
 *
 * Redis, Zookeeper 등 다양한 구현체로 교체 가능
 */
interface DistributedLockManager {

    /**
     * 락 획득 시도
     *
     * @param key 락 키
     * @param waitTime 락 획득 대기 시간, 0이면 즉시 반환
     * @param leaseTime 락 유지 시간
     * @param timeUnit 시간 단위
     * @return 락 획득 성공 여부
     */
    fun tryLock(key: String, waitTime: Long, leaseTime: Long, timeUnit: TimeUnit): Boolean

    /**
     * 락 해제
     *
     * @param key 락 키
     */
    fun unlock(key: String)
}
