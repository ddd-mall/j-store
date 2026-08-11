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

import java.time.Instant

enum class IntegrationMessageKind {
    EVENT,
    COMMAND,
}

data class IntegrationMessageEnvelope(
    val transportId: String,
    val messageId: String,
    val messageName: String,
    val messageVersion: Int,
    val messageKind: IntegrationMessageKind,
    val destination: String,
    val partitionKey: String,
    val correlationId: String,
    val causationId: String?,
    val tenantId: String?,
    val occurredAt: Instant,
    val payload: String,
    val orderingKey: String,
    val sequenceNo: Long,
) {
    init {
        require(transportId.isNotBlank()) { "transportId must not be blank" }
        require(orderingKey.isNotBlank()) { "orderingKey must not be blank" }
        require(sequenceNo > 0) { "sequenceNo must be positive" }
    }
}

/** SPI implemented by a concrete Kafka, AMQP, or cloud messaging adapter. */
interface IntegrationMessageTransport {
    val transportId: String

    /**
     * Returns only after the broker has acknowledged accepting the message. Implementations must
     * use [IntegrationMessageEnvelope.orderingKey] as their partition or routing key when the
     * target supports ordered partitions.
     */
    fun publish(envelope: IntegrationMessageEnvelope)
}
