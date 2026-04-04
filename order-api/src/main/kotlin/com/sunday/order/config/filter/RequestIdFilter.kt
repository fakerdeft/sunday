package com.sunday.order.config.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = request.getHeader(HEADER_REQUEST_ID) ?: UUID.randomUUID().toString().substring(0, 8)
        val memberId = request.getHeader(HEADER_USER_ID) ?: "anonymous"

        MDC.put(MDC_REQUEST_ID, requestId)
        MDC.put(MDC_MEMBER_ID, memberId)
        response.setHeader(HEADER_REQUEST_ID, requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }

    companion object {
        private const val HEADER_REQUEST_ID = "X-Request-ID"
        private const val HEADER_USER_ID = "X-USER-ID"
        const val MDC_REQUEST_ID = "requestId"
        const val MDC_MEMBER_ID = "memberId"
    }
}
