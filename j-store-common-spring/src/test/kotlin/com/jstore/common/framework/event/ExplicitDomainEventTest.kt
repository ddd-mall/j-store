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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class ExplicitDomainEventTest :
    FunSpec({
        data class StubExplicitEvent(
            override val source: Any = "aggregate-1",
            override val eventId: String = "event-1",
            override val eventName: String = "catalog.stub-explicit",
            override val eventVersion: Int = 3,
            override val occurredAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
            override val aggregateType: String = "Catalog",
            override val aggregateId: String = "aggregate-1",
        ) : ExplicitDomainEvent

        test("explicit domain event supplies stable metadata without reflection fallback") {
            val metadata = StubExplicitEvent().metadata

            metadata.eventId shouldBe "event-1"
            metadata.eventName shouldBe "catalog.stub-explicit"
            metadata.eventVersion shouldBe 3
            metadata.occurredAt shouldBe Instant.parse("2026-01-01T00:00:00Z")
            metadata.aggregateType shouldBe "Catalog"
            metadata.aggregateId shouldBe "aggregate-1"
        }
    })
