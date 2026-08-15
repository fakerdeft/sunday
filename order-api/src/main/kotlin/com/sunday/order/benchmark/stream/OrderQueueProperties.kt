package com.sunday.order.benchmark.stream

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Duration

@Profile("local")
@Component
@ConfigurationProperties(prefix = "sunday.order.queue")
data class OrderQueueProperties(
    var keyPrefix: String = "sunday:order:{queue}",
    var consumerGroup: String = "reservation-workers",
    var consumerName: String = "single-worker",
    var statusTtl: Duration = Duration.ofDays(7),
    var maxAttempts: Int = 3,
    var retryDelay: Duration = Duration.ofSeconds(1),
    var readBlockTimeout: Duration = Duration.ofSeconds(1),
    var maxMessagesPerCycle: Int = 100
) {
    val streamKey: String
        get() = "$keyPrefix:requests"
}
