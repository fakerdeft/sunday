package com.sunday.common.lock

import com.sunday.common.exception.LockAcquisitionException as LockAcquisitionMarker

/**
 * 분산 락 획득 실패 시 발생하는 예외
 */
class LockAcquisitionException(
    val key: String,
    message: String = "이미 처리 중인 요청입니다: $key"
) : RuntimeException(message), LockAcquisitionMarker
