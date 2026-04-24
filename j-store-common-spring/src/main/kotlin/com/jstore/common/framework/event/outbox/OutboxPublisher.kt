package com.jstore.common.framework.event.outbox

import com.jstore.common.framework.event.DomainEventBus
import org.slf4j.LoggerFactory
import java.time.Instant

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

    fun pollAndPublish() {
        try {
            val entries = outboxEntryRepository.findPendingAndRetryable(
                maxRetryCount = properties.maxRetryCount,
                batchSize = properties.batchSize
            )
            var successCount = 0
            var failCount = 0

            for (entry in entries) {
                try {
                    val event = eventSerializer.deserialize(entry.payload, entry.eventType)
                    domainEventBus.publishEvent(event)
                    outboxEntryRepository.save(
                        entry.copy(
                            status = OutboxEntryStatus.PUBLISHED,
                            updatedAt = Instant.now()
                        )
                    )
                    successCount++
                } catch (e: Exception) {
                    val newRetryCount = entry.retryCount + 1
                    val newStatus = if (newRetryCount >= properties.maxRetryCount)
                        OutboxEntryStatus.DEAD_LETTER else OutboxEntryStatus.FAILED
                    outboxEntryRepository.save(
                        entry.copy(
                            status = newStatus,
                            retryCount = newRetryCount,
                            updatedAt = Instant.now()
                        )
                    )
                    failCount++

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
}
