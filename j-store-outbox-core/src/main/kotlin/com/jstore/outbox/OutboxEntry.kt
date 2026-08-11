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

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat

/** Outbox 条目领域模型，表示一条待发布的领域事件记录。 */
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
    val partitionKey: String = aggregateId,
    val correlationId: String = eventId,
    val causationId: String? = null,
    val tenantId: String? = null,
    /** Stable stream identity within one transport. */
    val orderingKey: String,
    /** Strictly increasing position within (transportId, orderingKey). */
    val sequenceNo: Long,
) {
    init {
        require(id.isNotBlank()) { "Outbox entry ID must not be blank" }
        require(eventId.isNotBlank()) { "Outbox event/message ID must not be blank" }
        require(eventType.isNotBlank()) { "Outbox event/message type must not be blank" }
        require(eventClassName.isNotBlank()) { "Outbox class name must not be blank" }
        require(eventVersion > 0) { "Outbox event/message version must be positive" }
        require(aggregateType.isNotBlank()) { "Outbox aggregate type must not be blank" }
        require(aggregateId.isNotBlank()) { "Outbox aggregate ID must not be blank" }
        require(destination.isNotBlank()) { "Outbox destination must not be blank" }
        require(transportId.isNotBlank()) { "Outbox transport ID must not be blank" }
        require(partitionKey.isNotBlank()) { "Outbox partition key must not be blank" }
        require(correlationId.isNotBlank()) { "Outbox correlation ID must not be blank" }
        require(orderingKey.isNotBlank()) { "Outbox ordering key must not be blank" }
        require(sequenceNo > 0) { "Outbox sequence number must be positive" }
        require(retryCount >= 0) { "Outbox retry count must not be negative" }
        require(lockToken >= 0) { "Outbox lock token must not be negative" }

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

        when (messageKind) {
            OutboxMessageKind.DOMAIN_EVENT ->
                require(
                    deliveryTarget == OutboxDeliveryTarget.LOCAL_DOMAIN &&
                        transportId == OutboxTransportIds.LOCAL_DOMAIN
                ) {
                    "Domain events can only target local-domain"
                }
            OutboxMessageKind.INTEGRATION_EVENT,
            OutboxMessageKind.INTEGRATION_COMMAND ->
                require(
                    when (deliveryTarget) {
                        OutboxDeliveryTarget.LOCAL_DOMAIN -> false
                        OutboxDeliveryTarget.LOCAL_INTEGRATION ->
                            transportId == OutboxTransportIds.LOCAL
                        OutboxDeliveryTarget.BROKER ->
                            transportId != OutboxTransportIds.LOCAL_DOMAIN &&
                                transportId != OutboxTransportIds.LOCAL
                    }
                ) {
                    "Integration message delivery target and transport ID are inconsistent"
                }
        }
    }
}

enum class OutboxMessageKind {
    DOMAIN_EVENT,
    INTEGRATION_EVENT,
    INTEGRATION_COMMAND,
}

enum class OutboxDeliveryTarget {
    LOCAL_DOMAIN,
    LOCAL_INTEGRATION,
    BROKER,
}

val OutboxDeliveryTarget.defaultTransportId: String
    get() =
        when (this) {
            OutboxDeliveryTarget.LOCAL_DOMAIN -> OutboxTransportIds.LOCAL_DOMAIN
            OutboxDeliveryTarget.LOCAL_INTEGRATION -> OutboxTransportIds.LOCAL
            OutboxDeliveryTarget.BROKER -> OutboxTransportIds.LEGACY_BROKER
        }

object OutboxTransportIds {
    const val LOCAL_DOMAIN = "local-domain"
    const val LOCAL = "local"
    const val LEGACY_BROKER = "broker"
}

object OutboxOrderingKeys {
    fun domain(aggregateType: String, aggregateId: String): String =
        scoped(aggregateType, aggregateId)

    fun integration(destination: String, partitionKey: String): String =
        scoped(destination, partitionKey)

    private fun scoped(scope: String, key: String): String {
        require(scope.isNotBlank()) { "Ordering scope must not be blank" }
        require(key.isNotBlank()) { "Ordering key component must not be blank" }
        val scopeBytes = scope.toByteArray(StandardCharsets.UTF_8)
        val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
        val canonical =
            "${scopeBytes.size}:$scope:${keyBytes.size}:$key".toByteArray(StandardCharsets.UTF_8)
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical))
    }
}
