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

/** Immutable publication intent, independent of extraction and delivery state. */
data class OutboxMessage(
    val id: String,
    val eventType: String,
    val payload: String,
    val aggregateType: String,
    val aggregateId: String,
    val createdAt: Instant,
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
    require(id.isNotBlank()) { "Outbox entry ID must not be blank" }
    require(eventId.isNotBlank()) { "Outbox event/message ID must not be blank" }
    require(eventType.isNotBlank()) { "Outbox event/message type must not be blank" }
    require(eventClassName.isNotBlank()) { "Outbox class name must not be blank" }
    require(eventVersion > 0) { "Outbox event/message version must be positive" }
    require(aggregateType.isNotBlank()) { "Outbox aggregate type must not be blank" }
    require(aggregateId.isNotBlank()) { "Outbox aggregate ID must not be blank" }
    require(destination.isNotBlank()) { "Outbox destination must not be blank" }
    require(logicalDestination.isNotBlank()) { "Outbox logical destination must not be blank" }
    require(deliveryProfile.isNotBlank()) { "Outbox delivery profile must not be blank" }
    require(acceptBefore == null || !acceptBefore.isBefore(occurredAt)) {
      "Outbox accept-before deadline must not precede occurred-at"
    }
    require(transportId.isNotBlank()) { "Outbox transport ID must not be blank" }
    require(partitionKey.isNotBlank()) { "Outbox partition key must not be blank" }
    require(correlationId.isNotBlank()) { "Outbox correlation ID must not be blank" }
    require(merchantScopeId == null || merchantScopeId.isNotBlank()) {
      "Outbox merchant scope ID must be null or non-blank"
    }
    require(deploymentScopeId == null || deploymentScopeId.isNotBlank()) {
      "Outbox deployment scope ID must be null or non-blank"
    }
    require(orderingKey.isNotBlank()) { "Outbox ordering key must not be blank" }
    require(sequenceNo > 0) { "Outbox sequence number must be positive" }
    when (messageKind) {
      OutboxMessageKind.DOMAIN_EVENT -> {
        require(
            deliveryTarget == OutboxDeliveryTarget.LOCAL_DOMAIN &&
                transportId == OutboxTransportIds.LOCAL_DOMAIN
        ) {
          "Domain events can only target local-domain"
        }
        require(deliveryProfile == "LOCAL_DOMAIN" && acceptBefore == null) {
          "Domain events require LOCAL_DOMAIN profile and cannot have an acceptance deadline"
        }
      }
      OutboxMessageKind.INTEGRATION_EVENT,
      OutboxMessageKind.INTEGRATION_COMMAND -> {
        require(
            when (deliveryTarget) {
              OutboxDeliveryTarget.LOCAL_DOMAIN -> false
              OutboxDeliveryTarget.LOCAL_INTEGRATION -> transportId == OutboxTransportIds.LOCAL
              OutboxDeliveryTarget.BROKER ->
                  transportId != OutboxTransportIds.LOCAL_DOMAIN &&
                      transportId != OutboxTransportIds.LOCAL
            }
        ) {
          "Integration message delivery target and transport ID are inconsistent"
        }
        if (messageKind == OutboxMessageKind.INTEGRATION_EVENT) {
          require(acceptBefore == null) {
            "Integration events cannot have an acceptance deadline"
          }
        }
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
