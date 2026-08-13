package com.sunday.order.config.auth

import com.sunday.common.auth.UserId
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class UserIdArgumentResolver : HandlerMethodArgumentResolver {

    companion object {
        const val USER_ID_HEADER = "X-USER-ID"
    }

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.parameterType == Long::class.java &&
                parameter.hasParameterAnnotation(UserId::class.java)
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Long {
        val userIdHeader = webRequest.getHeader(USER_ID_HEADER) ?: throw MissingUserIdException()

        return try {
            userIdHeader.toLong()
        } catch (e: NumberFormatException) {
            throw InvalidUserIdException(userIdHeader, e)
        }
    }
}

class MissingUserIdException :
    IllegalArgumentException("필수 헤더가 누락되었습니다: ${UserIdArgumentResolver.USER_ID_HEADER}")

class InvalidUserIdException(value: String, cause: Throwable) :
    IllegalArgumentException("${UserIdArgumentResolver.USER_ID_HEADER} 헤더 값이 올바르지 않습니다: $value", cause)
