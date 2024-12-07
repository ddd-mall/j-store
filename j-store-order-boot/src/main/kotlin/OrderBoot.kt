package com.jstore

import com.jstore.common.utils.logging.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
open class OrderBoot


fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger(OrderBoot::class.java)
    logger.info("test {}", "any param")
    val log = LoggerFactory.getLogger("test logger")
    log.info("test logger")
    SpringApplication.run(OrderBoot::class.java, *args)
}

