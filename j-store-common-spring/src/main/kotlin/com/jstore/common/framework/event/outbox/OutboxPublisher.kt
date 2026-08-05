package com.jstore.common.framework.event.outbox

import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import org.slf4j.LoggerFactory

/**
 * Outbox 轮询投递器。
 *
 * 后台调度任务，轮询 Outbox 表中待投递的消息并交给目标通道路由器。 投递成功更新状态为 PUBLISHED；失败时 retryCount+1，达到上限标记为 DEAD_LETTER。
 */
class OutboxPublisher(
    private val outboxEntryRepository: OutboxEntryRepository,
    private val deliveryRouter: OutboxDeliveryRouter,
    private val properties: OutboxProperties,
    private val outboxMonitor: OutboxMonitor = NoopOutboxMonitor,
    private val transactionOperations: OutboxRelayTransactionOperations =
        ImmediateOutboxRelayTransactionOperations,
) {
    private val logger = LoggerFactory.getLogger(OutboxPublisher::class.java)
    private val workerId =
        properties.workerId.ifBlank {
            "outbox-${UUID.randomUUID()}"
        }

    fun pollAndPublish() {
        try {
            val now = Instant.now()
            val entries =
                outboxEntryRepository.claimPendingAndRetryable(
                    maxRetryCount = properties.maxRetryCount,
                    batchSize = min(properties.batchSize, properties.maxInFlightPerPoll),
                    lockedBy = workerId,
                    lockedUntil = now.plusMillis(properties.lockTimeoutMillis),
                )
            var successCount = 0
            var failCount = 0

            for (entry in entries) {
                try {
                    // Claimed rows always have a positive token. Token zero is retained only for
                    // compatibility with custom repositories during a rolling upgrade.
                    if (
                        entry.lockToken > 0 &&
                            !outboxEntryRepository.renewLease(
                                entry.id,
                                workerId,
                                entry.lockToken,
                                Instant.now().plusMillis(properties.lockTimeoutMillis),
                            )
                    ) {
                        throw OutboxLockOwnershipChangedException(entry.id, workerId)
                    }
                    val updated = transactionOperations.executeDelivery {
                        deliveryRouter.deliver(entry)
                        outboxEntryRepository
                            .markPublished(
                                entry.copy(
                                    status = OutboxEntryStatus.PUBLISHED,
                                    updatedAt = Instant.now(),
                                    lockedBy = null,
                                    lockedAt = null,
                                    lockedUntil = null,
                                    lastError = null,
                                ),
                                workerId,
                            )
                            .also { published ->
                                if (!published) {
                                    throw OutboxLockOwnershipChangedException(entry.id, workerId)
                                }
                            }
                    }
                    if (updated) {
                        successCount++
                    }
                } catch (e: Exception) {
                    val newRetryCount = entry.retryCount
                    val newStatus =
                        if (newRetryCount >= properties.maxRetryCount) OutboxEntryStatus.DEAD_LETTER
                        else OutboxEntryStatus.FAILED
                    val updated = transactionOperations.executeFailure {
                        outboxEntryRepository.markFailed(
                            entry.copy(
                                status = newStatus,
                                retryCount = newRetryCount,
                                updatedAt = Instant.now(),
                                nextAttemptAt = calculateNextAttemptAt(newRetryCount),
                                lockedBy = null,
                                lockedAt = null,
                                lockedUntil = null,
                                lastError = formatError(e),
                            ),
                            workerId,
                        )
                    }
                    if (updated) {
                        failCount++
                    } else {
                        logger.warn(
                            "Outbox entry failure result ignored because lock ownership changed: id={}, eventType={}, workerId={}",
                            entry.id,
                            entry.eventType,
                            workerId,
                        )
                    }

                    if (updated && newStatus == OutboxEntryStatus.DEAD_LETTER) {
                        outboxMonitor.recordDeadLetter(entry)
                        logger.warn(
                            "Outbox entry moved to DEAD_LETTER: id={}, eventType={}, retryCount={}",
                            entry.id,
                            entry.eventType,
                            newRetryCount,
                        )
                    }
                    logger.error(
                        "Failed to deliver outbox entry: id={}, eventType={}, error={}",
                        entry.id,
                        entry.eventType,
                        e.message,
                        e,
                    )
                }
            }

            logger.info("Outbox poll completed: delivered={}, failed={}", successCount, failCount)
            outboxMonitor.recordPoll(successCount, failCount)
        } catch (e: Exception) {
            logger.error("Outbox polling encountered an unexpected error", e)
            throw e
        }
    }

    private fun calculateNextAttemptAt(retryCount: Int): Instant {
        val cappedShift = min(max(retryCount - 1, 0), 30)
        val multiplier = 1L shl cappedShift
        val delayMillis =
            min(
                properties.initialRetryDelayMillis * multiplier,
                properties.maxRetryDelayMillis,
            )
        return Instant.now().plusMillis(delayMillis)
    }

    private fun formatError(e: Exception): String {
        val message = e.message?.let { ": $it" } ?: ""
        return "${e::class.java.name}$message".take(2000)
    }

    private class OutboxLockOwnershipChangedException(
        entryId: String,
        workerId: String,
    ) :
        IllegalStateException(
            "Outbox entry lock ownership changed before publish commit: id=$entryId, workerId=$workerId"
        )
}
