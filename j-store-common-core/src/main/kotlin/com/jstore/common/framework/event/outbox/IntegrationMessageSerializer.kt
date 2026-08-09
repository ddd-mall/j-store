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

import com.jstore.common.framework.messaging.IntegrationMessage
import java.util.concurrent.ConcurrentHashMap

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class IntegrationMessageType(
    val name: String,
    val version: Int = 1,
)

interface IntegrationMessageSerializer {
    fun serialize(message: IntegrationMessage): String

    fun deserialize(payload: String, messageName: String, messageVersion: Int): IntegrationMessage
}

interface IntegrationMessageTypeRegistry {
    fun register(
        messageName: String,
        messageVersion: Int,
        messageClass: Class<out IntegrationMessage>,
    )

    fun resolve(messageName: String, messageVersion: Int): Class<out IntegrationMessage>
}

class InMemoryIntegrationMessageTypeRegistry : IntegrationMessageTypeRegistry {
    private val types = ConcurrentHashMap<EventTypeKey, Class<out IntegrationMessage>>()

    override fun register(
        messageName: String,
        messageVersion: Int,
        messageClass: Class<out IntegrationMessage>,
    ) {
        val key = EventTypeKey(messageName, messageVersion)
        val existing = types.putIfAbsent(key, messageClass)
        require(existing == null || existing == messageClass) {
            "Duplicate @IntegrationMessageType registration: messageName=$messageName, " +
                "messageVersion=$messageVersion, existingClass=${existing?.name}, " +
                "duplicateClass=${messageClass.name}"
        }
    }

    override fun resolve(
        messageName: String,
        messageVersion: Int,
    ): Class<out IntegrationMessage> =
        types[EventTypeKey(messageName, messageVersion)]
            ?: throw OutboxSerializationException(
                "Unknown integration message type: messageName=$messageName, messageVersion=$messageVersion"
            )
}
