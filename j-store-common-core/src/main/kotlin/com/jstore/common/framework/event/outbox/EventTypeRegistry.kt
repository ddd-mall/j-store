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

import com.jstore.common.framework.event.DomainEvent
import java.util.concurrent.ConcurrentHashMap

data class EventTypeKey(
    val eventName: String,
    val eventVersion: Int,
)

interface EventTypeRegistry {
    fun register(eventName: String, eventVersion: Int, eventClass: Class<out DomainEvent>)

    fun resolve(eventName: String, eventVersion: Int): Class<out DomainEvent>
}

class InMemoryEventTypeRegistry : EventTypeRegistry {
    private val eventTypes = ConcurrentHashMap<EventTypeKey, Class<out DomainEvent>>()

    override fun register(
        eventName: String,
        eventVersion: Int,
        eventClass: Class<out DomainEvent>,
    ) {
        val key = EventTypeKey(eventName, eventVersion)
        val existing = eventTypes.putIfAbsent(key, eventClass)
        require(existing == null || existing == eventClass) {
            "Duplicate @DomainEventType registration: eventName=$eventName, eventVersion=$eventVersion, " +
                "existingClass=${existing?.name}, duplicateClass=${eventClass.name}"
        }
    }

    override fun resolve(eventName: String, eventVersion: Int): Class<out DomainEvent> {
        eventTypes[EventTypeKey(eventName, eventVersion)]?.let {
            return it
        }

        throw OutboxSerializationException(
            "无法识别的事件类型: eventName=$eventName, eventVersion=$eventVersion"
        )
    }
}
