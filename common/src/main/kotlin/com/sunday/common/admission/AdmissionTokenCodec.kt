package com.sunday.common.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 대기열 입장 증표를 만들고 검증한다.
 *
 * 대기열 서버가 입장을 허가하면서 발급하고, 주문 서버는 서명만 검증한다.
 * 두 서버가 저장소를 공유하거나 서로를 호출하지 않고도 입장 여부를 확인할 수 있게 하는 것이 목적이다.
 *
 * 형식은 `base64url(payload).base64url(HMAC-SHA256(payload))` 이며,
 * payload 는 `회원ID:상품ID:만료시각(ms)` 이다.
 */
class AdmissionTokenCodec(secret: String) {
    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private const val SEPARATOR = '.'
        private const val PAYLOAD_FIELD_COUNT = 3
    }

    private val key = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM)
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    init {
        require(secret.isNotBlank()) { "입장 증표 비밀키가 비어 있습니다." }
    }

    fun issue(memberId: Long, productId: Long, expiresAt: Instant): String {
        val payload = "$memberId:$productId:${expiresAt.toEpochMilli()}"
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)

        return encoder.encodeToString(payloadBytes) + SEPARATOR + encoder.encodeToString(sign(payloadBytes))
    }

    fun verify(
        token: String?,
        memberId: Long,
        productId: Long,
        now: Instant = Instant.now()
    ): AdmissionTokenResult {
        if (token.isNullOrBlank()) {

            return AdmissionTokenResult.MISSING
        }

        val separatorIndex = token.indexOf(SEPARATOR)

        if (separatorIndex <= 0 || separatorIndex == token.length - 1) {

            return AdmissionTokenResult.MALFORMED
        }

        val payloadBytes = try {
            decoder.decode(token.substring(0, separatorIndex))
        } catch (e: IllegalArgumentException) {

            return AdmissionTokenResult.MALFORMED
        }
        val signature = try {
            decoder.decode(token.substring(separatorIndex + 1))
        } catch (e: IllegalArgumentException) {

            return AdmissionTokenResult.MALFORMED
        }

        // 서명을 먼저 확인해야 내용이 조작된 증표로 다른 판정에 들어가지 않는다.
        if (!MessageDigest.isEqual(sign(payloadBytes), signature)) {

            return AdmissionTokenResult.BAD_SIGNATURE
        }

        val fields = String(payloadBytes, StandardCharsets.UTF_8).split(':')

        if (fields.size != PAYLOAD_FIELD_COUNT) {

            return AdmissionTokenResult.MALFORMED
        }

        val tokenMemberId = fields[0].toLongOrNull()
        val tokenProductId = fields[1].toLongOrNull()
        val expiresAt = fields[2].toLongOrNull()

        if (tokenMemberId == null || tokenProductId == null || expiresAt == null) {

            return AdmissionTokenResult.MALFORMED
        }
        if (tokenMemberId != memberId || tokenProductId != productId) {

            return AdmissionTokenResult.MISMATCHED
        }
        if (expiresAt <= now.toEpochMilli()) {

            return AdmissionTokenResult.EXPIRED
        }

        return AdmissionTokenResult.VALID
    }

    private fun sign(payload: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)

        mac.init(key)

        return mac.doFinal(payload)
    }
}

enum class AdmissionTokenResult {
    VALID,

    /** 증표가 아예 오지 않음 */
    MISSING,

    /** 형식이 깨짐 */
    MALFORMED,

    /** 서명이 맞지 않음. 위조되었거나 비밀키가 다름 */
    BAD_SIGNATURE,

    /** 다른 회원이나 다른 상품의 증표 */
    MISMATCHED,

    /** 입장 유효 시간이 지남 */
    EXPIRED;

    fun isValid(): Boolean = this == VALID
}
