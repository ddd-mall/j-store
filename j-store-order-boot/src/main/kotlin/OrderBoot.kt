package com.jstore

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
open class OrderBoot


fun main(args: Array<String>) {
    SpringApplication.run(OrderBoot::class.java, *args)
}