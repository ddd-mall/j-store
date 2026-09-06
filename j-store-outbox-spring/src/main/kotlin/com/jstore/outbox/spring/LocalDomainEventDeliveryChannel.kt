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
package com.jstore.outbox.spring

import com.jstore.common.framework.event.LocalDomainEventBus
import com.jstore.common.framework.event.PreparingLocalDomainEventBus
import com.jstore.outbox.*

class LocalDomainEventDeliveryChannel(
    private val eventSerializer: EventSerializer,
    private val localDomainEventBus: LocalDomainEventBus,
) : PreparingOutboxDeliveryChannel {
    override val transportId: String = OutboxTransportIds.LOCAL_DOMAIN

    override fun deliver(entry: OutboxEntry) = prepare(entry).invoke()

    override fun prepare(entry: OutboxEntry): () -> Unit {
        check(entry.transportId == transportId) {
            "LOCAL_DOMAIN channel cannot deliver transport ${entry.transportId}"
        }
        check(entry.messageKind == OutboxMessageKind.DOMAIN_EVENT) {
            "LOCAL_DOMAIN channel only accepts DOMAIN_EVENT, actual=${entry.messageKind}"
        }
        val event =
            eventSerializer.deserialize(
                entry.payload,
                entry.eventType,
                entry.eventVersion,
            )
        return if (localDomainEventBus is PreparingLocalDomainEventBus)
            localDomainEventBus.prepareEvent(event)
        else {
            { localDomainEventBus.publishEvent(event) }
        }
    }
}
