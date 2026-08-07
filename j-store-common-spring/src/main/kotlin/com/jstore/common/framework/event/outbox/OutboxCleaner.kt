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
package com.jstore.common.framework.event.outbox

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
) {
    private val logger = LoggerFactory.getLogger(OutboxCleaner::class.java)

    fun cleanup() {
        try {
            val before = Instant.now().minus(properties.retentionDays.toLong(), ChronoUnit.DAYS)
            val deleted =
                outboxEntryRepository.deletePublishedBefore(before, properties.cleanupBatchSize)
            logger.info(
                "Outbox cleanup completed: deleted={}, retentionDays={}",
                deleted,
                properties.retentionDays,
            )
        } catch (e: Exception) {
            logger.error("Outbox cleanup encountered an unexpected error", e)
        }
    }
}
