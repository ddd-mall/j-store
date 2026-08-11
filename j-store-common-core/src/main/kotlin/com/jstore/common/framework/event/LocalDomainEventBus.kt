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
 * 本进程领域事件总线。
 *
 * 只负责同步调用当前进程内的领域事件监听器，不表示远程消息投递，也不提供事务一致性或可靠投递保证。 事务性发布由 [DomainEventPublisher] 和 Outbox 基础设施负责。
 */
interface LocalDomainEventBus {
    fun publishEvent(domainEvent: DomainEvent)

    fun register(domainEventListener: DomainEventListener<*>)

    fun unregister(domainEventListener: DomainEventListener<*>)
}
