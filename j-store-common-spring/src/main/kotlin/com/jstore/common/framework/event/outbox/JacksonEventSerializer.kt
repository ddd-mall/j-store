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

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.common.framework.event.DomainEvent

/** 基于 Jackson 的事件序列化/反序列化实现。 */
class JacksonEventSerializer(
    private val objectMapper: ObjectMapper,
    private val eventTypeRegistry: EventTypeRegistry = InMemoryEventTypeRegistry(),
    private val eventUpcasterRegistry: EventUpcasterRegistry = InMemoryEventUpcasterRegistry(),
) : EventSerializer {

    override fun serialize(event: DomainEvent): String {
        return objectMapper.writeValueAsString(event)
    }

    override fun deserialize(payload: String, eventName: String, eventVersion: Int): DomainEvent {
        val upcasted = eventUpcasterRegistry.upcast(eventName, eventVersion, payload)
        val clazz = eventTypeRegistry.resolve(upcasted.eventName, upcasted.eventVersion)
        return try {
            objectMapper.readValue(upcasted.payload, clazz) as DomainEvent
        } catch (e: Exception) {
            val summary =
                if (upcasted.payload.length > 200) upcasted.payload.substring(0, 200) + "..."
                else upcasted.payload
            throw OutboxSerializationException(
                "JSON 反序列化失败, eventName=${upcasted.eventName}, eventVersion=${upcasted.eventVersion}, payload=$summary",
                e,
            )
        }
    }
}
