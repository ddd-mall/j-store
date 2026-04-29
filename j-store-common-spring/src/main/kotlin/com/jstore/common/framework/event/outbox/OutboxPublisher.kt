package com.jstore.common.framework.event.outbox

import com.jstore.common.framework.event.DomainEventBus
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Outbox 轮询投递器。
 *
 * 后台调度任务，轮询 Outbox 表中待投递的事件并分发到 DomainEventBus。
 * 投递成功更新状态为 PUBLISHED；失败时 retryCount+1，达到上限标记为 DEAD_LETTER。
 */
class OutboxPublisher(
    private val outboxEntryRepository: OutboxEntryRepository,
    private val eventSerializer: EventSerializer,
    private val domainEventBus: DomainEventBus,
    private val properties: OutboxProperties,
) {
    private val logger = LoggerFactory.getLogger(OutboxPublisher::class.java)
    private val workerId = properties.workerId.ifBlank {
        "outbox-${UUID.randomUUID()}"
    }

    fun pollAndPublish() {
        try {
            val now = Instant.now()
            val entries = outboxEntryRepository.claimPendingAndRetryable(
                maxRetryCount = properties.maxRetryCount,
                batchSize = properties.batchSize,
                lockedBy = workerId,
                lockedUntil = now.plusMillis(properties.lockTimeoutMillis)
            )
            var successCount = 0
            var failCount = 0

            for (entry in entries) {
                try {
                    val event = eventSerializer.deserialize(entry.payload, entry.eventType)
                    domainEventBus.publishEvent(event)
                    val updated = outboxEntryRepository.markPublished(
                        entry.copy(
                            status = OutboxEntryStatus.PUBLISHED,
                            updatedAt = Instant.now(),
                            lockedBy = null,
                            lockedAt = null,
                            lockedUntil = null,
                            lastError = null
                        ),
                        workerId
                    )
                    if (updated) {
                        successCount++
                    } else {
                        logger.warn(
                            "Outbox entry publish result ignored because lock ownership changed: id={}, eventType={}, workerId={}",
                            entry.id, entry.eventType, workerId
                        )
                    }
                } catch (e: Exception) {
                    val newRetryCount = entry.retryCount
                    val newStatus = if (newRetryCount >= properties.maxRetryCount)
                        OutboxEntryStatus.DEAD_LETTER else OutboxEntryStatus.FAILED
                    val updated = outboxEntryRepository.markFailed(
                        entry.copy(
                            status = newStatus,
                            retryCount = newRetryCount,
                            updatedAt = Instant.now(),
                            nextAttemptAt = calculateNextAttemptAt(newRetryCount),
                            lockedBy = null,
                            lockedAt = null,
                            lockedUntil = null,
                            lastError = formatError(e)
                        ),
                        workerId
                    )
                    if (updated) {
                        failCount++
                    } else {
                        logger.warn(
                            "Outbox entry failure result ignored because lock ownership changed: id={}, eventType={}, workerId={}",
                            entry.id, entry.eventType, workerId
                        )
                    }

                    if (newStatus == OutboxEntryStatus.DEAD_LETTER) {
                        logger.warn(
                            "Outbox entry moved to DEAD_LETTER: id={}, eventType={}, retryCount={}",
                            entry.id, entry.eventType, newRetryCount
                        )
                    }
                    logger.error(
                        "Failed to deliver outbox entry: id={}, eventType={}, error={}",
                        entry.id, entry.eventType, e.message, e
                    )
                }
            }

            logger.info("Outbox poll completed: delivered={}, failed={}", successCount, failCount)
        } catch (e: Exception) {
            logger.error("Outbox polling encountered an unexpected error", e)
        }
    }

    private fun calculateNextAttemptAt(retryCount: Int): Instant {
        val cappedShift = min(max(retryCount - 1, 0), 30)
        val multiplier = 1L shl cappedShift
        val delayMillis = min(
            properties.initialRetryDelayMillis * multiplier,
            properties.maxRetryDelayMillis
        )
        return Instant.now().plusMillis(delayMillis)
    }

    private fun formatError(e: Exception): String {
        val message = e.message?.let { ": $it" } ?: ""
        return "${e::class.java.name}$message".take(2000)
    }
}
