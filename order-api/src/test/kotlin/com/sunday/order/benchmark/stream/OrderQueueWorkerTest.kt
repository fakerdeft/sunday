package com.sunday.order.benchmark.stream

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

class OrderQueueWorkerTest {

    @Test
    fun `empty queue waits for up to one batch of new messages`() {
        val redis = mockk<StringRedisTemplate>()
        val stream = mockk<StreamOperations<String, String, String>>()
        val capturedOptions = mutableListOf<StreamReadOptions>()
        val properties = OrderQueueProperties(
            readBlockTimeout = Duration.ofMillis(250),
            maxMessagesPerCycle = 100
        )
        val worker = OrderQueueWorker(
            redis = redis,
            properties = properties,
            queueService = mockk(),
            reservationService = mockk()
        )

        every { redis.opsForStream<String, String>() } returns stream
        every {
            stream.read(
                any<Consumer>(),
                capture(capturedOptions),
                any<StreamOffset<String>>()
            )
        } returns emptyList()

        worker.processAvailableMessages()

        assertThat(capturedOptions).hasSize(2)
        assertThat(capturedOptions[0].count).isEqualTo(100L)
        assertThat(capturedOptions[0].isBlocking).isFalse()
        assertThat(capturedOptions[1].count).isEqualTo(100L)
        assertThat(capturedOptions[1].isBlocking).isTrue()
        assertThat(capturedOptions[1].block).isEqualTo(250L)
    }
}
