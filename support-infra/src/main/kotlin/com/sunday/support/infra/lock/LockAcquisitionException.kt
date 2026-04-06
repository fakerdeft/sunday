package com.sunday.support.infra.lock

import com.sunday.common.exception.LockAcquisitionException as LockAcquisitionMarker

class LockAcquisitionException(
    val key: String,
    message: String = "이미 처리 중인 요청입니다: $key"
) : RuntimeException(message), LockAcquisitionMarker
