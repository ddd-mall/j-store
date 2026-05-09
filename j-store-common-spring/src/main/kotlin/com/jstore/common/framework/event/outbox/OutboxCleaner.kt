package com.jstore.common.framework.event.outbox

import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Outbox 定期清理器。
 *
 * 删除已超过保留期限的 PUBLISHED 状态条目，保留 DEAD_LETTER 条目供人工排查。
 */
class OutboxCleaner(
    private val outboxEntryRepository: OutboxEntryRepository,
    private val properties: OutboxProperties,
) {
    private val logger = LoggerFactory.getLogger(OutboxCleaner::class.java)

    fun cleanup() {
        try {
            val before = Instant.now().minus(properties.retentionDays.toLong(), ChronoUnit.DAYS)
            val deleted = outboxEntryRepository.deletePublishedBefore(before, properties.cleanupBatchSize)
            logger.info("Outbox cleanup completed: deleted={}, retentionDays={}", deleted, properties.retentionDays)
        } catch (e: Exception) {
            logger.error("Outbox cleanup encountered an unexpected error", e)
        }
    }
}
