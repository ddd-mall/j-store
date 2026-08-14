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

import com.jstore.messaging.MessageConsumptionRetentionRepository
import com.jstore.outbox.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory

/**
 * Outbox 定期清理器。
 *
 * 删除已超过保留期限的 PUBLISHED 状态条目，保留 DEAD_LETTER 条目供人工排查。
 */
class OutboxCleaner(
    private val outboxEntryRepository: OutboxEntryRepository,
    private val properties: OutboxProperties,
    private val consumptionRetentionRepository: MessageConsumptionRetentionRepository,
) {
    private val logger = LoggerFactory.getLogger(OutboxCleaner::class.java)

    fun cleanup() {
        try {
            val before = Instant.now().minus(properties.retentionDays.toLong(), ChronoUnit.DAYS)
            val outboxCleanup = drainBatches {
                outboxEntryRepository.deletePublishedBefore(before, properties.cleanupBatchSize)
            }
            val consumptionBefore =
                Instant.now().minus(properties.consumptionRetentionDays.toLong(), ChronoUnit.DAYS)
            val consumptionCleanup = drainBatches {
                consumptionRetentionRepository.deleteConsumptionsBefore(
                    consumptionBefore,
                    properties.cleanupBatchSize,
                )
            }
            val streamCleanup = drainBatches {
                consumptionRetentionRepository.deleteInactiveStreamPositionsBefore(
                    consumptionBefore,
                    properties.cleanupBatchSize,
                )
            }
            logger.info(
                "Outbox cleanup completed: outboxDeleted={}, consumptionDeleted={}, streamPositionsDeleted={}, batches={}/{}/{}, retentionDays={}, consumptionRetentionDays={}",
                outboxCleanup.deleted,
                consumptionCleanup.deleted,
                streamCleanup.deleted,
                outboxCleanup.batches,
                consumptionCleanup.batches,
                streamCleanup.batches,
                properties.retentionDays,
                properties.consumptionRetentionDays,
            )
        } catch (e: Exception) {
            logger.error("Outbox cleanup encountered an unexpected error", e)
        }
    }

    private fun drainBatches(deleteBatch: () -> Int): CleanupResult {
        var deleted = 0
        var batches = 0
        while (batches < properties.cleanupMaxBatchesPerRun) {
            val batchDeleted = deleteBatch()
            deleted += batchDeleted
            batches++
            if (batchDeleted < properties.cleanupBatchSize) break
        }
        return CleanupResult(deleted, batches)
    }

    private data class CleanupResult(val deleted: Int, val batches: Int)
}
