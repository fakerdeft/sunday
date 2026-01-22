package com.sunday.support.infra.lock

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.context.expression.MethodBasedEvaluationContext
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.stereotype.Component

/**
 * SpEL 표현식 파서
 *
 * 분산 락 키 등 동적 값을 SpEL 표현식으로 생성할 때 사용
 */
@Component
class CustomSpringELParser {

    private val parser = SpelExpressionParser()
    private val parameterNameDiscoverer = DefaultParameterNameDiscoverer()

    /**
     * SpEL 표현식을 파싱하여 문자열 생성
     *
     * @param joinPoint AOP JoinPoint
     * @param expression SpEL 표현식
     * @return 파싱된 문자열
     *
     * 예: "'order:' + #userId + ':' + #request.productId" → "order:123:456"
     */
    fun parseKey(joinPoint: ProceedingJoinPoint, expression: String): String {
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val args = joinPoint.args

        val context = MethodBasedEvaluationContext(
            joinPoint.target,
            method,
            args,
            parameterNameDiscoverer
        )

        return parser.parseExpression(expression).getValue(context, String::class.java)
            ?: throw IllegalArgumentException("SpEL 표현식을 파싱할 수 없습니다: $expression")
    }
}
