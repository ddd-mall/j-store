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
package com.jstore.outbox.spring.messaging

import com.jstore.outbox.OutboxTransportIds
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jstore.messaging")
data class MessagingProperties(val targets: Set<String> = setOf(OutboxTransportIds.LOCAL)) {
    init {
        require(targets.isNotEmpty()) { "jstore.messaging.targets must not be empty" }
        require(targets.all { it.isNotBlank() }) {
            "jstore.messaging.targets must contain only non-blank transport IDs"
        }
        require(OutboxTransportIds.LOCAL_DOMAIN !in targets) {
            "local-domain is reserved for domain events"
        }
    }
}
