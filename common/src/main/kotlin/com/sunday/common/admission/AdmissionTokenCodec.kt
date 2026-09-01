package com.sunday.common.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 게이트가 발급하고 주문 서버가 서명만 검증하는 통행증.
 * 두 서버가 저장소를 공유하지 않고도 입장 여부를 확인하기 위한 것이다.
 *
 * `base64url(payload).base64url(HMAC-SHA256(payload))`, payload 는 `회원ID:상품ID:만료시각(ms)`.
 */
class AdmissionTokenCodec(secret: String) {
    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private const val SEPARATOR = '.'
        private const val PAYLOAD_FIELD_COUNT = 3
        private const val FINGERPRINT_ALGORITHM = "SHA-256"

        /** 통행증 지문. 예약 키로 쓰기 위한 것이라 비밀키가 필요 없다. */
        fun fingerprint(token: String): String =
            MessageDigest.getInstance(FINGERPRINT_ALGORITHM)
                .digest(token.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
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

        // 서명 우선 확인. 조작된 내용으로 이후 판정에 들어가지 않게 한다.
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

    MISSING,

    MALFORMED,

    BAD_SIGNATURE,

    MISMATCHED,

    EXPIRED;

    fun isValid(): Boolean = this == VALID
}
