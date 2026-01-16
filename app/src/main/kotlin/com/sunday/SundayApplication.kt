package com.sunday

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SundayApplication

fun main(args: Array<String>) {
    runApplication<SundayApplication>(*args)
}
