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
 * 领域事件监听器接口
 *
 * 设计原则：
 * 1. 纯领域模型，完全脱离框架依赖
 * 2. 泛型约束：T 为该监听器处理的具体事件类型
 * 3. 具体实现类通过泛型参数声明支持的事件类型
 */
interface DomainEventListener<T : DomainEvent> {
    /**
     * Stable listener identifier used as the consumer key for idempotent event handling.
     *
     * Use a durable, business-owned name before renaming or moving listener classes.
     */
    fun listenerId(): String

    /**
     * 处理领域事件
     *
     * @param event 具体的领域事件实例
     */
    fun onDomainEvent(event: T)
}
