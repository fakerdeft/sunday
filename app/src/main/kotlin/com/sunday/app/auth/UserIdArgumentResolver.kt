package com.sunday.app.auth

import com.sunday.common.auth.UserId
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * UserId Argument Resolver (전역 공통 인증)
 *
 * HTTP 헤더의 X-USER-ID 값을 읽어서 Long 타입으로 변환하여 주입
 *
 * **모든 도메인(Member, Account, Payment, Order)에서 공통으로 사용**
 *
 * 이 방식의 장점:
 * 1. JWT 토큰 발급/검증 불필요 (시간 절약)
 * 2. 부하 테스트 시 헤더 값만 바꾸면 1만 명 동시 접속 흉내 가능
 * 3. 프론트엔드 로그인 화면 불필요
 * 4. JMeter/k6 스크립트 작성 간단
 */
@Component
class UserIdArgumentResolver : HandlerMethodArgumentResolver {

    companion object {
        const val USER_ID_HEADER = "X-USER-ID"
    }

    /**
     * @UserId 어노테이션이 붙은 Long 타입 파라미터를 지원
     */
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.parameterType == Long::class.java &&
                parameter.hasParameterAnnotation(UserId::class.java)
    }

    /**
     * HTTP 헤더에서 X-USER-ID를 읽어서 Long으로 변환
     */
    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Long {
        val userIdHeader = webRequest.getHeader(USER_ID_HEADER)
            ?: throw MissingUserIdException()

        return try {
            userIdHeader.toLong()
        } catch (e: NumberFormatException) {
            throw InvalidUserIdException(userIdHeader, e)
        }
    }
}

/**
 * X-USER-ID 헤더가 없을 때 발생하는 예외
 */
class MissingUserIdException : IllegalArgumentException("Missing required header: ${UserIdArgumentResolver.USER_ID_HEADER}")

/**
 * X-USER-ID 헤더 값이 유효하지 않을 때 발생하는 예외
 */
class InvalidUserIdException(value: String, cause: Throwable) :
    IllegalArgumentException("Invalid ${UserIdArgumentResolver.USER_ID_HEADER} header value: $value", cause)
