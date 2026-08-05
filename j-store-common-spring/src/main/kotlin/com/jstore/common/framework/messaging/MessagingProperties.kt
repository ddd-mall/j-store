package com.jstore.common.framework.messaging

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jstore.messaging")
data class MessagingProperties(val mode: IntegrationMessagingMode = IntegrationMessagingMode.LOCAL)
