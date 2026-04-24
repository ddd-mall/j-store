package com.jstore

//import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableScheduling


@EnableJpaAuditing
@SpringBootApplication
//@EnableDiscoveryClient
@EnableConfigurationProperties
@EnableScheduling
class JStoreOrderBootApplication


fun main(args: Array<String>) {
    SpringApplication.run(JStoreOrderBootApplication::class.java, *args)
}
