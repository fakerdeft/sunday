package com.sunday.common.auth

/**
 * 가짜 인증 - HTTP 헤더에서 Member ID를 주입받는 어노테이션
 *
 * **모든 도메인에서 공통으로 사용**
 *
 * 사용법:
 * ```kotlin
 * @PostMapping("/orders")
 * fun createOrder(@UserId memberId: Long, @RequestBody request: OrderRequest) {
 *     // memberId는 X-USER-ID 헤더에서 자동 주입됨
 * }
 * ```
 *
 * 요청 예시:
 * ```bash
 * curl -H "X-USER-ID: 105" -X POST http://localhost:8080/api/orders
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class UserId
