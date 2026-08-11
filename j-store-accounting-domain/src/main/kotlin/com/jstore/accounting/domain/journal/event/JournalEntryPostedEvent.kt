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
package com.jstore.accounting.domain.journal.event

import com.jstore.accounting.domain.journal.JournalEntryId
import com.jstore.accounting.domain.journal.JournalEntryType
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import java.time.Instant
import java.time.LocalDate

@DomainEventType(name = "accounting.journal-entry-posted", version = 1)
data class JournalEntryPostedEvent(
    val entryId: JournalEntryId,
    val entryNo: String,
    val entryType: JournalEntryType,
    val accountingDate: LocalDate,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {

    override val eventName: String
        get() = "accounting.journal-entry-posted"

    override val eventVersion: Int
        get() = 1

    override val aggregateType: String
        get() = "JournalEntry"

    override val aggregateId: String
        get() = entryId.toString()
}
