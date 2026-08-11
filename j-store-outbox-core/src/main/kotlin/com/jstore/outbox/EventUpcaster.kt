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
package com.jstore.outbox

data class UpcastedEventPayload(
    val eventName: String,
    val eventVersion: Int,
    val payload: String,
)

interface EventUpcaster {
    val eventName: String
    val sourceVersion: Int
    val targetVersion: Int

    fun upcast(payload: String): String
}

interface EventUpcasterRegistry {
    fun register(upcaster: EventUpcaster)

    fun upcast(eventName: String, eventVersion: Int, payload: String): UpcastedEventPayload
}

class InMemoryEventUpcasterRegistry(upcasters: Iterable<EventUpcaster> = emptyList()) :
    EventUpcasterRegistry {
    private val upcasters = linkedMapOf<EventTypeKey, EventUpcaster>()

    init {
        upcasters.forEach(::register)
    }

    override fun register(upcaster: EventUpcaster) {
        require(upcaster.targetVersion > upcaster.sourceVersion) {
            "Event upcaster targetVersion must be greater than sourceVersion: eventName=${upcaster.eventName}, " +
                "sourceVersion=${upcaster.sourceVersion}, targetVersion=${upcaster.targetVersion}"
        }
        val key = EventTypeKey(upcaster.eventName, upcaster.sourceVersion)
        val existing = upcasters[key]
        require(existing == null) {
            "Duplicate EventUpcaster registration: eventName=${upcaster.eventName}, " +
                "sourceVersion=${upcaster.sourceVersion}, existing=${existing!!::class.java.name}, " +
                "duplicate=${upcaster::class.java.name}"
        }
        upcasters[key] = upcaster
    }

    override fun upcast(
        eventName: String,
        eventVersion: Int,
        payload: String,
    ): UpcastedEventPayload {
        var currentVersion = eventVersion
        var currentPayload = payload

        while (true) {
            val upcaster = upcasters[EventTypeKey(eventName, currentVersion)] ?: break
            currentPayload = upcaster.upcast(currentPayload)
            currentVersion = upcaster.targetVersion
        }

        return UpcastedEventPayload(
            eventName = eventName,
            eventVersion = currentVersion,
            payload = currentPayload,
        )
    }
}
