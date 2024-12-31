package com.jstore

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@EnableJpaAuditing
@SpringBootApplication
class OrderBoot
fun main(args: Array<String>) {
    SpringApplication.run(OrderBoot::class.java, *args)
}