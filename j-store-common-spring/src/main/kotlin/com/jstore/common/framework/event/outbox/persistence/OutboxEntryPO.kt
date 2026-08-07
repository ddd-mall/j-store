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
package com.jstore.common.framework.event.outbox.persistence

import com.jstore.common.framework.event.outbox.OutboxEntryStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "outbox_entry")
class OutboxEntryPO(
    @Id @Column(name = "id", length = 36) var id: String = "",
    @Column(name = "event_type", nullable = false, length = 512) var eventType: String = "",
    @Column(name = "event_id", nullable = false, length = 64) var eventId: String = "",
    @Column(name = "event_class_name", nullable = false, length = 512)
    var eventClassName: String = "",
    @Column(name = "event_version", nullable = false) var eventVersion: Int = 1,
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT") var payload: String = "",
    @Column(name = "aggregate_type", nullable = false, length = 256) var aggregateType: String = "",
    @Column(name = "aggregate_id", nullable = false, length = 128) var aggregateId: String = "",
    @Column(name = "occurred_at", nullable = false) var occurredAt: Instant = Instant.now(),
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OutboxEntryStatus = OutboxEntryStatus.PENDING,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Column(name = "retry_count", nullable = false) var retryCount: Int = 0,
    @Column(name = "next_attempt_at", nullable = false) var nextAttemptAt: Instant = Instant.now(),
    @Column(name = "locked_by", length = 128) var lockedBy: String? = null,
    @Column(name = "locked_at") var lockedAt: Instant? = null,
    @Column(name = "locked_until") var lockedUntil: Instant? = null,
    @Column(name = "lock_token", nullable = false) var lockToken: Long = 0,
    @Column(name = "last_error", columnDefinition = "TEXT") var lastError: String? = null,
)
