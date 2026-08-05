package com.jstore.outbox.operations

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OutboxOperationsProperties::class)
class OutboxOperationsConfiguration
