package com.jstore

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.data.jpa.repository.config.EnableJpaAuditing


@EnableJpaAuditing
@SpringBootApplication
@EnableDiscoveryClient
class JStoreOrderBootApplication


fun main(args: Array<String>) {
    SpringApplication.run(JStoreOrderBootApplication::class.java, *args)
}
