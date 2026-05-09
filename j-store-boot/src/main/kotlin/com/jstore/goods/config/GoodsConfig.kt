package com.jstore.com.jstore.goods.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "jstore.outbox", name = ["enabled"], havingValue = "true")
class GoodsConfig {

}