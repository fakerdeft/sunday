package com.sunday.gate.application

import com.sunday.common.admission.AdmissionTokenCodec
import com.sunday.gate.config.OrderPassProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 주문 서버 앞단의 문지기다.
 *
 * 재고가 남은 만큼만 통과시키고 나머지는 여기서 품절로 끝낸다.
 * 순번이나 정원 같은 개념은 없다. 통과하거나 품절이거나 둘 중 하나이며 즉시 답한다.
 *
 * 발급 가능 수량(budget)은 스케줄러가 주기마다 `남은 재고 - 발급했지만 아직 주문하지 않은 수` 로
 * 다시 계산해 넣고, 그 사이에는 발급할 때마다 줄이기만 한다.
 * 요청을 처리하면서 재고를 다시 읽지 않기 때문에, 낡은 재고 값과 최신 발급 수가 섞여
 * 통행증이 재고보다 많이 나가는 일이 생기지 않는다.
 *
 * budget 은 재고의 원본이 아니라 매 주기 다시 계산되는 파생값이다.
 * 어긋나더라도 다음 주기에 맞춰지고, 최종 판정은 언제나 주문 서버의 `SKIP LOCKED` 가 한다.
 */
@Service
class OrderPassService(
    private val redis: StringRedisTemplate,
    private val properties: OrderPassProperties,
    private val tokenCodec: AdmissionTokenCodec
) {
    companion object {
        /**
         * 통과 판정. 발급 가능 수량을 하나 줄여 보고, 남아 있으면 통과시킨다.
         * 이미 통행증을 가진 회원은 같은 통행증을 그대로 돌려주어 재요청에 안전하다.
         *
         * KEYS: budget, inFlight
         * ARGV: memberId, nowMillis, passTtlMillis, keyTtlSeconds
         */
        private val passScript = DefaultRedisScript(
            """
            local existing = redis.call('ZSCORE', KEYS[2], ARGV[1])
            if existing and tonumber(existing) > tonumber(ARGV[2]) then
                return 'PASSED|' .. existing
            end

            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 'SOLD_OUT|0'
            end

            local remaining = redis.call('DECR', KEYS[1])
            if remaining < 0 then
                redis.call('INCR', KEYS[1])

                return 'SOLD_OUT|0'
            end

            local expireAt = tonumber(ARGV[2]) + tonumber(ARGV[3])
            redis.call('ZADD', KEYS[2], expireAt, ARGV[1])
            redis.call('EXPIRE', KEYS[2], ARGV[4])

            return 'PASSED|' .. expireAt
            """.trimIndent(),
            String::class.java
        )

        /**
         * 발급 가능 수량 재계산. 만료된 통행증을 회수한 뒤 같은 시점의 값으로 한 번에 정한다.
         *
         * KEYS: budget, inFlight
         * ARGV: nowMillis, availableStock, keyTtlSeconds
         */
        private val syncScript = DefaultRedisScript(
            """
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])

            local inFlight = redis.call('ZCARD', KEYS[2])
            local budget = tonumber(ARGV[2]) - inFlight

            if budget < 0 then
                budget = 0
            end

            redis.call('SET', KEYS[1], budget, 'EX', ARGV[3])

            return tostring(budget)
            """.trimIndent(),
            String::class.java
        )
    }

    fun requestPass(productId: Long, memberId: Long): OrderPass {
        val now = Instant.now()
        val result = redis.execute(
            passScript,
            listOf(properties.budgetKey(productId), properties.inFlightKey(productId)),
            memberId.toString(),
            now.toEpochMilli().toString(),
            properties.passTtl.toMillis().toString(),
            properties.keyTtlSeconds().toString()
        ) ?: error("Redis 가 통행증 판정 결과를 반환하지 않았습니다.")

        val values = result.split('|')

        if (values.size != 2) {
            error("통행증 응답 형식이 올바르지 않습니다: $result")
        }

        if (values[0] != OrderPassStatus.PASSED.name) {

            return OrderPass(productId, memberId, OrderPassStatus.SOLD_OUT, null, null)
        }

        val expiresAt = Instant.ofEpochMilli(values[1].toDouble().toLong())

        return OrderPass(
            productId = productId,
            memberId = memberId,
            status = OrderPassStatus.PASSED,
            // 증표는 만료 시각에서 그대로 만들어 낸다. 따로 저장하지 않는다.
            token = tokenCodec.issue(memberId, productId, expiresAt),
            expiresAt = expiresAt
        )
    }

    /** 주문 서버에서 읽어 온 재고로 발급 가능 수량을 다시 계산한다. */
    fun syncBudget(productId: Long, availableStock: Long): Long {
        val budget = redis.execute(
            syncScript,
            listOf(properties.budgetKey(productId), properties.inFlightKey(productId)),
            Instant.now().toEpochMilli().toString(),
            availableStock.coerceAtLeast(0).toString(),
            properties.keyTtlSeconds().toString()
        )

        return budget?.toLongOrNull() ?: 0L
    }

    /** 주문을 마쳤거나 포기한 회원의 통행증을 회수한다. */
    fun release(productId: Long, memberId: Long) {
        redis.opsForZSet().remove(properties.inFlightKey(productId), memberId.toString())
    }

    fun budget(productId: Long): Long =
        redis.opsForValue().get(properties.budgetKey(productId))?.toLongOrNull() ?: 0L

    fun inFlightCount(productId: Long): Long =
        redis.opsForZSet().size(properties.inFlightKey(productId)) ?: 0L

    /** 반복 측정 시 이전 실행 상태가 남지 않도록 지운다. */
    fun reset(productId: Long) {
        redis.delete(listOf(properties.budgetKey(productId), properties.inFlightKey(productId)))
    }
}
