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
package com.jstore.user.domain.useraccount.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.UserId
import java.time.Instant

/** 用户账号注册事件 */
@DomainEventType(name = "user.account-registered", version = 1)
data class UserAccountRegisteredEvent(
    override val source: Any,
    val userId: UserId,
    val phoneNumber: PhoneNumber,
    override val occurredAt: Instant = Instant.now(),
) : ExplicitDomainEvent {
    override val eventName: String = "user.account-registered"
    override val eventVersion: Int = 1
    override val aggregateType: String = "UserAccount"
    override val aggregateId: String = userId.value.toString()
    override val eventId: String =
        stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
