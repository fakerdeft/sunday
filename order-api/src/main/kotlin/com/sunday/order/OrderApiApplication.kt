package com.sunday.order

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class OrderApiApplication

fun main(args: Array<String>) {
    runApplication<OrderApiApplication>(*args)
}
