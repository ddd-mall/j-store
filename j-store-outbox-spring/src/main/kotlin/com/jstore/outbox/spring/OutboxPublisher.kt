/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.outbox.spring

import com.jstore.outbox.*
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

    fun pollAndPublish(): Int {
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
                    check(entry.status == OutboxEntryStatus.IN_PROGRESS && entry.lockToken > 0) {
                        "Claimed outbox entry must hold a positive fencing token: id=${entry.id}"
                    }
                    if (
                        !outboxEntryRepository.renewLease(
                            entry.id,
                            workerId,
                            entry.lockToken,
                            Instant.now().plusMillis(properties.lockTimeoutMillis),
                        )
                    ) {
                        throw OutboxLockOwnershipChangedException(entry.id, workerId)
                    }
                    val delivery = deliveryRouter.prepare(entry)
                    val updated = transactionOperations.executeDelivery {
                        delivery()
                        val publishedAt = Instant.now()
                        outboxEntryRepository
                            .markPublished(
                                entry.copy(
                                    status = OutboxEntryStatus.PUBLISHED,
                                    updatedAt = publishedAt,
                                    publishedAt = publishedAt,
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
                        outboxMonitor.recordDelivery(entry, true)
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
                        outboxMonitor.recordDelivery(entry, false)
                    } else {
                        logger.warn(
                            "Outbox entry failure result ignored because lock ownership changed: id={}, eventType={}, transportId={}, workerId={}",
                            entry.id,
                            entry.eventType,
                            entry.transportId,
                            workerId,
                        )
                    }

                    if (updated && newStatus == OutboxEntryStatus.DEAD_LETTER) {
                        outboxMonitor.recordDeadLetter(entry)
                        logger.warn(
                            "Outbox entry moved to DEAD_LETTER: id={}, eventType={}, transportId={}, retryCount={}",
                            entry.id,
                            entry.eventType,
                            entry.transportId,
                            newRetryCount,
                        )
                    }
                    logger.error(
                        "Failed to deliver outbox entry: id={}, eventType={}, transportId={}, error={}",
                        entry.id,
                        entry.eventType,
                        entry.transportId,
                        e.message,
                        e,
                    )
                }
            }

            logger.info("Outbox poll completed: delivered={}, failed={}", successCount, failCount)
            outboxMonitor.recordPoll(successCount, failCount)
            return entries.size
        } catch (e: Exception) {
            logger.error("Outbox polling encountered an unexpected error", e)
            throw e
        }
    }

    /** Reclaims newly-ready work until the queue is empty or this drain spends its batch budget. */
    fun drainAndPublish(): OutboxDrainResult {
        var processedBatches = 0
        repeat(properties.maxBatchesPerDrain) {
            if (pollAndPublish() == 0) {
                return OutboxDrainResult(processedBatches, budgetExhausted = false)
            }
            processedBatches++
        }
        return OutboxDrainResult(processedBatches, budgetExhausted = true)
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

data class OutboxDrainResult(val processedBatches: Int, val budgetExhausted: Boolean)
