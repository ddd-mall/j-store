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

import com.jstore.common.framework.event.DomainEvent
import java.time.Instant

/** Atomic inbox/idempotency port shared by local and broker-delivered messages. */
fun interface MessageConsumptionRepository {
    fun tryStart(
        consumerId: String,
        messageId: String,
        messageName: String,
        messageVersion: Int,
    ): Boolean

    fun tryStartOrdered(
        consumerId: String,
        messageId: String,
        messageName: String,
        messageVersion: Int,
        deliveryOrder: MessageDeliveryOrder,
    ): Boolean =
        throw UnsupportedOperationException(
            "Ordered delivery requires a sequence-aware consumption repository"
        )
}

/** Retention operations kept separate from the hot-path consumption contract. */
interface MessageConsumptionRetentionRepository {
    fun deleteConsumptionsBefore(before: Instant, batchSize: Int): Int

    fun deleteInactiveStreamPositionsBefore(before: Instant, batchSize: Int): Int
}

data class MessageDeliveryOrder(
    val transportId: String,
    val orderingKey: String,
    val sequenceNo: Long,
) {
    init {
        require(transportId.isNotBlank()) { "transportId must not be blank" }
        require(orderingKey.isNotBlank()) { "orderingKey must not be blank" }
        require(sequenceNo > 0) { "sequenceNo must be positive" }
    }
}

object BuiltInMessageConsumerIds {
    const val LOCAL_INTEGRATION_BUS = "jstore.local-integration-bus"
}

class MessageSequenceGapException(
    consumerId: String,
    transportId: String,
    orderingKey: String,
    expected: Long,
    actual: Long,
) :
    IllegalStateException(
        "Message sequence gap: consumerId=$consumerId, transportId=$transportId, " +
            "orderingKey=$orderingKey, " +
            "expected=$expected, actual=$actual"
    )

fun MessageConsumptionRepository.tryStart(consumerId: String, event: DomainEvent): Boolean =
    tryStart(
        consumerId = consumerId,
        messageId = event.eventId,
        messageName = event.eventName,
        messageVersion = event.eventVersion,
    )
