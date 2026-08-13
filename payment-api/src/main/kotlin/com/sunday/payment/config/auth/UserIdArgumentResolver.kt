package com.sunday.payment.config.auth

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
    IllegalArgumentException("Missing required header: ${UserIdArgumentResolver.USER_ID_HEADER}")

class InvalidUserIdException(value: String, cause: Throwable) :
    IllegalArgumentException("Invalid ${UserIdArgumentResolver.USER_ID_HEADER} header value: $value", cause)
