package com.jstore

import com.jstore.common.utils.Logger
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
open class OrderBoot


fun main(args: Array<String>) {
    Logger.info("test")
    SpringApplication.run(OrderBoot::class.java, *args)
}

