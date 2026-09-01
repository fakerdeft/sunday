package com.sunday.gate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/** 게이트 서버. 자체 DB 없이 Redis 만 쓴다. JPA 는 클래스패스에서 제외돼 있다. */
@SpringBootApplication
@EnableScheduling
class GateApiApplication

fun main(args: Array<String>) {
    runApplication<GateApiApplication>(*args)
}
