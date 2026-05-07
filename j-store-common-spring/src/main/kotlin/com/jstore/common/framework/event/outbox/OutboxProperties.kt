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
    val initialRetryDelayMillis: Long = 1000,
    val maxRetryDelayMillis: Long = 60000,
    val lockTimeoutMillis: Long = 300000,
    val workerId: String = "",
    val retentionDays: Int = 7,
    val cleanupBatchSize: Int = 500,
    val cleanupCron: String = "0 0 3 * * ?",
    val eventTypeScanPackages: List<String> = listOf("com.jstore"),
    val asyncMulticasterFailFast: Boolean = false,
) {
    init {
        require(pollingInterval > 0) { "jstore.outbox.polling-interval must be greater than 0" }
        require(batchSize > 0) { "jstore.outbox.batch-size must be greater than 0" }
        require(maxRetryCount > 0) { "jstore.outbox.max-retry-count must be greater than 0" }
        require(initialRetryDelayMillis >= 0) { "jstore.outbox.initial-retry-delay-millis must be greater than or equal to 0" }
        require(maxRetryDelayMillis >= initialRetryDelayMillis) {
            "jstore.outbox.max-retry-delay-millis must be greater than or equal to initial retry delay"
        }
        require(lockTimeoutMillis > 0) { "jstore.outbox.lock-timeout-millis must be greater than 0" }
        require(retentionDays >= 0) { "jstore.outbox.retention-days must be greater than or equal to 0" }
        require(cleanupBatchSize > 0) { "jstore.outbox.cleanup-batch-size must be greater than 0" }
        require(eventTypeScanPackages.isNotEmpty()) { "jstore.outbox.event-type-scan-packages must not be empty" }
    }
}
