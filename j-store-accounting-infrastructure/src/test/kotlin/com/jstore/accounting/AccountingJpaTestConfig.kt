package com.jstore.accounting

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.jstore.accounting")
@EnableJpaRepositories("com.jstore.accounting")
class AccountingJpaTestConfig
