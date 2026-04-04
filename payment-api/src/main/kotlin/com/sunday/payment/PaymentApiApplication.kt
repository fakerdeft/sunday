package com.sunday.payment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PaymentApiApplication

fun main(args: Array<String>) {
    runApplication<PaymentApiApplication>(*args)
}
