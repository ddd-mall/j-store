package com.jstore.common.framework.event.outbox

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Outbox 模式配置属性。
 */
@ConfigurationProperties(prefix = "jstore.outbox")
data class OutboxProperties(
    val enabled: Boolean = false,
    val pollingInterval: Long = 5000,
    val batchSize: Int = 100,
    val maxRetryCount: Int = 5,
    val retentionDays: Int = 7,
    val cleanupBatchSize: Int = 500,
    val cleanupCron: String = "0 0 3 * * ?"
)
