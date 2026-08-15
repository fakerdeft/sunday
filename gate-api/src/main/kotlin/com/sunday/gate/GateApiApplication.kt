package com.sunday.gate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 대기열 서버다. 자체 데이터베이스를 갖지 않고 Redis 만 사용한다.
 * 재고는 주문 서버에 물어보고, 입장을 허가할 때 증표를 발급한다.
 */
@SpringBootApplication
@EnableScheduling
class GateApiApplication

fun main(args: Array<String>) {
    runApplication<GateApiApplication>(*args)
}
