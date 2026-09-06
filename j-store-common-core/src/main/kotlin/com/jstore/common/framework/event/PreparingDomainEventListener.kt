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
 * Prepares read-only external facts before delivery; the returned action runs in the delivery
 * transaction. Preparation can repeat and must not perform durable business writes or irreversible
 * side effects.
 */
interface PreparingDomainEventListener<T : DomainEvent> : DomainEventListener<T> {
    fun prepare(event: T): () -> Unit
}

/** Optional two-phase local delivery for listeners requiring external reads. */
interface PreparingLocalDomainEventBus : LocalDomainEventBus {
    fun prepareEvent(event: DomainEvent): () -> Unit
}
