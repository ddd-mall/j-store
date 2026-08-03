package com.jstore.order.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jstore.order")
data class OrderMerchantProperties(val merchantId: Long? = null) {init {
    require(merchantId != null && merchantId > 0) { "jstore.order.merchant-id must be configured as a positive number" }
}
}
