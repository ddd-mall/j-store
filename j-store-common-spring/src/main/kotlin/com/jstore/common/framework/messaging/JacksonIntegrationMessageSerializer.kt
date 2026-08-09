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
package com.jstore.common.framework.messaging

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.common.framework.event.outbox.IntegrationMessageSerializer
import com.jstore.common.framework.event.outbox.IntegrationMessageTypeRegistry
import com.jstore.common.framework.event.outbox.OutboxSerializationException

class JacksonIntegrationMessageSerializer(
    private val objectMapper: ObjectMapper,
    private val typeRegistry: IntegrationMessageTypeRegistry,
) : IntegrationMessageSerializer {
    override fun serialize(message: IntegrationMessage): String =
        objectMapper.writeValueAsString(message)

    override fun deserialize(
        payload: String,
        messageName: String,
        messageVersion: Int,
    ): IntegrationMessage =
        try {
            objectMapper
                .readerFor(typeRegistry.resolve(messageName, messageVersion))
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(payload)
        } catch (exception: Exception) {
            throw OutboxSerializationException(
                "Failed to deserialize integration message: messageName=$messageName, " +
                    "messageVersion=$messageVersion",
                exception,
            )
        }
}
