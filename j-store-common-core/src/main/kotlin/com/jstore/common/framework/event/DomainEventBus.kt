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

/**
 * 领域事件消息总线。
 *
 * 仅负责本进程内事件分发，不提供事务一致性或可靠投递保证。 业务代码需要事务性发布时应依赖 DomainEventPublisher。
 */
interface DomainEventBus {
    fun publishEvent(domainEvent: DomainEvent)

    fun register(domainEventListener: DomainEventListener<*>)

    fun unregister(domainEventListener: DomainEventListener<*>)
}
