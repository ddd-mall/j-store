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
package com.jstore.common.framework.event

import java.time.Instant

data class StubDomainEvent(
    override val eventId: String = "event-1",
    override val eventName: String = "test.stub-event",
    override val eventVersion: Int = 1,
    override val occurredAt: Instant = Instant.parse("2025-01-01T00:00:00Z"),
    override val aggregateType: String = "Test",
    override val aggregateId: String = "1",
) : DomainEvent
