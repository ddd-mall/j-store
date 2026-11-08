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

import com.jstore.outbox.OutboxBackend
import org.springframework.boot.context.properties.ConfigurationProperties

/** Publication settings shared by all Outbox implementations. */
@ConfigurationProperties(prefix = "jstore.outbox")
data class OutboxPublicationProperties(
    val enabled: Boolean = false,
    val mode: String = "polling",
    val eventTypeScanPackages: List<String> = listOf("com.jstore"),
) {
  init {
    require(mode.isNotBlank()) { "jstore.outbox.mode must not be blank" }
    require(eventTypeScanPackages.isNotEmpty()) { "Event type scan packages must not be empty" }
  }
}

/** Validated single owner; deliberately does not use @Primary to hide duplicate backends. */
data class OutboxBackendSelection(val backend: OutboxBackend)
