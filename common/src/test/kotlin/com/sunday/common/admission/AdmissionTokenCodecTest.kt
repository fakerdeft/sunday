package com.sunday.common.admission

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class AdmissionTokenCodecTest {

    private val codec = AdmissionTokenCodec("test-secret")
    private val now: Instant = Instant.parse("2026-08-15T00:00:00Z")

    @Test
    fun `발급한 증표는 같은 회원과 상품에서 검증된다`() {
        val token = codec.issue(memberId = 1L, productId = 10L, expiresAt = now.plusSeconds(60))

        assertEquals(AdmissionTokenResult.VALID, codec.verify(token, 1L, 10L, now))
    }

    @Test
    fun `증표가 없으면 거절한다`() {
        assertEquals(AdmissionTokenResult.MISSING, codec.verify(null, 1L, 10L, now))
        assertEquals(AdmissionTokenResult.MISSING, codec.verify("  ", 1L, 10L, now))
    }

    @Test
    fun `형식이 깨진 증표는 거절한다`() {
        assertEquals(AdmissionTokenResult.MALFORMED, codec.verify("서명없음", 1L, 10L, now))
        assertEquals(AdmissionTokenResult.MALFORMED, codec.verify(".onlySignature", 1L, 10L, now))
        assertEquals(AdmissionTokenResult.MALFORMED, codec.verify("onlyPayload.", 1L, 10L, now))
    }

    @Test
    fun `다른 비밀키로 만든 증표는 거절한다`() {
        val forged = AdmissionTokenCodec("another-secret")
            .issue(memberId = 1L, productId = 10L, expiresAt = now.plusSeconds(60))

        assertEquals(AdmissionTokenResult.BAD_SIGNATURE, codec.verify(forged, 1L, 10L, now))
    }

    @Test
    fun `내용을 바꾼 증표는 서명 검증에서 걸린다`() {
        val token = codec.issue(memberId = 1L, productId = 10L, expiresAt = now.plusSeconds(60))
        val tampered = codec.issue(memberId = 2L, productId = 10L, expiresAt = now.plusSeconds(60))
            .substringBefore('.') + "." + token.substringAfter('.')

        assertEquals(AdmissionTokenResult.BAD_SIGNATURE, codec.verify(tampered, 2L, 10L, now))
    }

    @Test
    fun `다른 회원이나 다른 상품의 증표는 거절한다`() {
        val token = codec.issue(memberId = 1L, productId = 10L, expiresAt = now.plusSeconds(60))

        assertEquals(AdmissionTokenResult.MISMATCHED, codec.verify(token, 2L, 10L, now))
        assertEquals(AdmissionTokenResult.MISMATCHED, codec.verify(token, 1L, 99L, now))
    }

    @Test
    fun `유효 시간이 지난 증표는 거절한다`() {
        val token = codec.issue(memberId = 1L, productId = 10L, expiresAt = now.plusSeconds(60))

        assertEquals(AdmissionTokenResult.VALID, codec.verify(token, 1L, 10L, now.plusSeconds(59)))
        assertEquals(AdmissionTokenResult.EXPIRED, codec.verify(token, 1L, 10L, now.plusSeconds(60)))
        assertEquals(AdmissionTokenResult.EXPIRED, codec.verify(token, 1L, 10L, now.plusSeconds(61)))
    }
}
