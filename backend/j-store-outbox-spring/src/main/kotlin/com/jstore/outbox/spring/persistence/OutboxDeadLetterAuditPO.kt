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
package com.jstore.outbox.spring.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "outbox_dead_letter_audit")
class OutboxDeadLetterAuditPO(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "outbox_entry_id", nullable = false, length = 36) var outboxEntryId: String = "",
    @Column(name = "event_id", length = 64) var eventId: String? = null,
    @Column(name = "operator_id", nullable = false, length = 128) var operatorId: String = "",
    @Column(name = "action", nullable = false, length = 32) var action: String = "",
    @Column(name = "reason", nullable = false, length = 1000) var reason: String = "",
    @Column(name = "result", nullable = false, length = 32) var result: String = "",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
)
