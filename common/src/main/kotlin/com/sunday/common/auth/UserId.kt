package com.sunday.common.auth

/** 인증 대체. `X-USER-ID` 헤더를 그대로 신뢰하므로 운영 인증 수단이 아니다. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class UserId
