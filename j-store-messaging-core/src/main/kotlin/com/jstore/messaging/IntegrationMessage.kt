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
package com.jstore.messaging

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

/** A stable message contract crossing a bounded-context or process boundary. */
interface IntegrationMessage {
    val messageId: String
    val messageName: String
    val messageVersion: Int
    val occurredAt: Instant
    val partitionKey: String
    val correlationId: String
    val causationId: String?
        get() = null

    val tenantId: String?
        get() = null

    val destination: String

    val metadata: IntegrationMessageMetadata
        get() =
            IntegrationMessageMetadata(
                messageId = messageId,
                messageName = messageName,
                messageVersion = messageVersion,
                occurredAt = occurredAt,
                partitionKey = partitionKey,
                correlationId = correlationId,
                causationId = causationId,
                tenantId = tenantId,
            )
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class IntegrationMessageType(
    val name: String,
    val version: Int = 1,
)

/** A fact that may be observed by zero or more consumers. */
interface IntegrationEvent : IntegrationMessage

/** An intention addressed to one logical owning context. */
interface IntegrationCommand : IntegrationMessage

data class IntegrationMessageMetadata(
    val messageId: String,
    val messageName: String,
    val messageVersion: Int,
    val occurredAt: Instant,
    val partitionKey: String,
    val correlationId: String,
    val causationId: String? = null,
    val tenantId: String? = null,
) {
    init {
        require(messageId.isNotBlank()) { "messageId must not be blank" }
        require(messageName.isNotBlank()) { "messageName must not be blank" }
        require(messageVersion > 0) { "messageVersion must be greater than zero" }
        require(partitionKey.isNotBlank()) { "partitionKey must not be blank" }
        require(correlationId.isNotBlank()) { "correlationId must not be blank" }
        require(causationId == null || causationId.isNotBlank()) {
            "causationId must be null or non-blank"
        }
        require(tenantId == null || tenantId.isNotBlank()) { "tenantId must be null or non-blank" }
    }
}

interface IntegrationMessagePublisher {
    fun publish(message: IntegrationMessage)
}

fun stableIntegrationMessageId(
    messageName: String,
    messageVersion: Int,
    partitionKey: String,
    occurredAt: Instant,
): String =
    UUID.nameUUIDFromBytes(
            "$messageName|$messageVersion|$partitionKey|$occurredAt"
                .toByteArray(StandardCharsets.UTF_8)
        )
        .toString()
