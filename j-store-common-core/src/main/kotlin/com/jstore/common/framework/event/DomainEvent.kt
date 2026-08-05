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
package com.jstore.common.framework.event

import java.time.Instant
import java.util.UUID

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DomainEventType(
    val name: String,
    val version: Int = 1,
)

/** Immutable domain fact emitted by an aggregate or domain service. */
interface DomainEvent {
    val eventId: String
    val eventName: String
    val eventVersion: Int
    val occurredAt: Instant
    val aggregateType: String
    val aggregateId: String

    /** Stable envelope metadata used by outbox delivery, diagnostics, and idempotent consumers. */
    val metadata: DomainEventMetadata
        get() =
            DomainEventMetadata(
                eventId = eventId,
                eventName = eventName,
                eventVersion = eventVersion,
                occurredAt = occurredAt,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
            )
}

/** Creates the stable ID once when a new event instance is constructed. */
fun newDomainEventId(): String = UUID.randomUUID().toString()

data class DomainEventMetadata(
    val eventId: String,
    val eventName: String,
    val eventVersion: Int,
    val occurredAt: Instant,
    val aggregateType: String,
    val aggregateId: String,
)
