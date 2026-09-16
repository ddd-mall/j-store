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
package com.jstore.outbox.spring.polling

import com.jstore.outbox.*
import java.time.Instant

/** Polling delivery task. Immutable publication metadata is validated by OutboxMessage. */
data class OutboxEntry(
    val id: String,
    val eventType: String,
    val payload: String,
    val aggregateType: String,
    val aggregateId: String,
    val status: OutboxEntryStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val retryCount: Int = 0,
    val nextAttemptAt: Instant = createdAt,
    val lockedBy: String? = null,
    val lockedAt: Instant? = null,
    val lockedUntil: Instant? = null,
    /** Monotonically increasing claim generation used to fence stale workers. */
    val lockToken: Long = 0,
    val lastError: String? = null,
    val eventId: String = id,
    val eventClassName: String = eventType,
    val eventVersion: Int = 1,
    val occurredAt: Instant = createdAt,
    val messageKind: OutboxMessageKind = OutboxMessageKind.DOMAIN_EVENT,
    val deliveryTarget: OutboxDeliveryTarget = OutboxDeliveryTarget.LOCAL_DOMAIN,
    val transportId: String = deliveryTarget.defaultTransportId,
    val destination: String = eventType,
    /** Stable bounded-context destination, independent from broker topic/queue naming. */
    val logicalDestination: String = destination,
    /** Operational delivery policy selected for this publication. */
    val deliveryProfile: String =
        if (messageKind == OutboxMessageKind.DOMAIN_EVENT) "LOCAL_DOMAIN" else "STANDARD",
    /** Optional command acceptance deadline propagated end-to-end. */
    val acceptBefore: Instant? = null,
    /** Time at which this delivery was confirmed as published. */
    val publishedAt: Instant? = null,
    val partitionKey: String = aggregateId,
    val correlationId: String = eventId,
    val causationId: String? = null,
    /** Optional merchant isolation scope; never a deployment or site identity. */
    val merchantScopeId: String? = null,
    /** Optional deployment/site routing extension, independent from merchant scope. */
    val deploymentScopeId: String? = null,
    /** Stable stream identity within one transport. */
    val orderingKey: String,
    /** Strictly increasing position within (transportId, orderingKey). */
    val sequenceNo: Long,
) {
    init {
        toMessage()
        require(retryCount >= 0) { "Outbox retry count must not be negative" }
        require(lockToken >= 0) { "Outbox lock token must not be negative" }
        require((status == OutboxEntryStatus.PUBLISHED) == (publishedAt != null)) {
            "Only PUBLISHED outbox entries must have a publication time"
        }

        val hasCompleteLease = lockedBy != null && lockedAt != null && lockedUntil != null
        if (status == OutboxEntryStatus.IN_PROGRESS) {
            require(hasCompleteLease) { "IN_PROGRESS outbox entry requires a complete lease" }
            require(lockedUntil.isAfter(lockedAt)) {
                "Outbox lease expiry must be after its acquisition time"
            }
        } else {
            require(lockedBy == null && lockedAt == null && lockedUntil == null) {
                "Only IN_PROGRESS outbox entries may hold a lease"
            }
        }
    }

    fun toMessage(): OutboxMessage =
        OutboxMessage(
            id = id,
            eventType = eventType,
            payload = payload,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            createdAt = createdAt,
            eventId = eventId,
            eventClassName = eventClassName,
            eventVersion = eventVersion,
            occurredAt = occurredAt,
            messageKind = messageKind,
            deliveryTarget = deliveryTarget,
            transportId = transportId,
            destination = destination,
            logicalDestination = logicalDestination,
            deliveryProfile = deliveryProfile,
            acceptBefore = acceptBefore,
            partitionKey = partitionKey,
            correlationId = correlationId,
            causationId = causationId,
            merchantScopeId = merchantScopeId,
            deploymentScopeId = deploymentScopeId,
            orderingKey = orderingKey,
            sequenceNo = sequenceNo,
        )

    companion object {
        fun pending(message: OutboxMessage): OutboxEntry =
            OutboxEntry(
                id = message.id,
                eventType = message.eventType,
                payload = message.payload,
                aggregateType = message.aggregateType,
                aggregateId = message.aggregateId,
                createdAt = message.createdAt,
                eventId = message.eventId,
                eventClassName = message.eventClassName,
                eventVersion = message.eventVersion,
                occurredAt = message.occurredAt,
                messageKind = message.messageKind,
                deliveryTarget = message.deliveryTarget,
                transportId = message.transportId,
                destination = message.destination,
                logicalDestination = message.logicalDestination,
                deliveryProfile = message.deliveryProfile,
                acceptBefore = message.acceptBefore,
                partitionKey = message.partitionKey,
                correlationId = message.correlationId,
                causationId = message.causationId,
                merchantScopeId = message.merchantScopeId,
                deploymentScopeId = message.deploymentScopeId,
                orderingKey = message.orderingKey,
                sequenceNo = message.sequenceNo,
                status = OutboxEntryStatus.PENDING,
                updatedAt = message.createdAt,
            )
    }
}
