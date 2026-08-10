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
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

data class DomainEventConsumptionId(
    var listenerId: String = "",
    var eventId: String = "",
) : Serializable

@Entity
@IdClass(DomainEventConsumptionId::class)
@Table(name = "domain_event_consumption")
class DomainEventConsumptionPO(
    @Id @Column(name = "listener_id", nullable = false, length = 512) var listenerId: String = "",
    @Id @Column(name = "event_id", nullable = false, length = 64) var eventId: String = "",
    @Column(name = "event_name", nullable = false, length = 256) var eventName: String = "",
    @Column(name = "event_version", nullable = false) var eventVersion: Int = 1,
    @Column(name = "consumed_at", nullable = false) var consumedAt: Instant = Instant.now(),
)
