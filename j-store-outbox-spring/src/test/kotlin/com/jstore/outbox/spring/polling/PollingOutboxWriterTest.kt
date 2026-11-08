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
import com.jstore.outbox.spring.OutboxRelaySignal
import java.time.Instant
import kotlin.test.*
import org.mockito.kotlin.*

class PollingOutboxWriterTest {
  @Test
  fun `polling repository does not expose unaudited dead-letter requeue`() {
    assertTrue(OutboxEntryRepository::class.java.methods.none { it.name == "requeueDeadLetters" })
  }

  @Test
  fun `append preserves full immutable intent and initializes polling state`() {
    val repository = mock<OutboxEntryRepository>()
    val signal = mock<OutboxRelaySignal>()
    val writer = PollingOutboxWriter(repository, signal)
    val first = message()
    val second = first.copy(id = "second", sequenceNo = 2)
    writer.append(listOf(first, second))
    val entries = argumentCaptor<List<OutboxEntry>>()
    inOrder(repository, signal) {
      verify(repository).saveAll(entries.capture())
      verify(signal).signalAfterCommit()
    }
    assertEquals(listOf(first, second), entries.firstValue.map { it.toMessage() })
    entries.firstValue.forEach {
      assertEquals(OutboxEntryStatus.PENDING, it.status)
      assertEquals(0, it.retryCount)
      assertEquals(0L, it.lockToken)
      assertNull(it.publishedAt)
      assertNull(it.lockedBy)
    }
  }

  @Test
  fun `failure propagates without a relay hint`() {
    val repository = mock<OutboxEntryRepository>()
    val signal = mock<OutboxRelaySignal>()
    whenever(repository.saveAll(any())).thenThrow(IllegalStateException("storage unavailable"))
    assertFailsWith<IllegalStateException> {
      PollingOutboxWriter(repository, signal).append(listOf(message()))
    }
    verifyNoInteractions(signal)
  }

  @Test
  fun `empty append has no persistence or wakeup`() {
    val repository = mock<OutboxEntryRepository>()
    val signal = mock<OutboxRelaySignal>()
    PollingOutboxWriter(repository, signal).append(emptyList())
    verifyNoInteractions(repository, signal)
  }

  private fun message() =
      OutboxMessage(
          id = "entry",
          eventId = "message",
          eventType = "inventory.reserve",
          eventClassName = "Reserve",
          eventVersion = 3,
          payload = "{\"quantity\":2}",
          aggregateType = "inventory.commands",
          aggregateId = "42",
          createdAt = Instant.EPOCH,
          occurredAt = Instant.EPOCH,
          messageKind = OutboxMessageKind.INTEGRATION_COMMAND,
          deliveryTarget = OutboxDeliveryTarget.BROKER,
          transportId = "kafka",
          destination = "inventory-v3",
          logicalDestination = "inventory.commands",
          deliveryProfile = "CRITICAL",
          acceptBefore = Instant.EPOCH.plusSeconds(30),
          partitionKey = "42",
          correlationId = "checkout",
          causationId = "authorized",
          merchantScopeId = "merchant",
          deploymentScopeId = "site",
          orderingKey = "stream",
          sequenceNo = 1,
      )
}
