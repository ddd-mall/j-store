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
package com.jstore.outbox

interface OutboxDeliveryChannel {
    val transportId: String

    fun deliver(entry: OutboxEntry)
}

class OutboxDeliveryRouter(private val channels: List<OutboxDeliveryChannel>) {
    fun deliver(entry: OutboxEntry) {
        val matching = channels.filter { it.transportId == entry.transportId }
        check(matching.size == 1) {
            "Expected exactly one outbox delivery channel for transportId=${entry.transportId}, found=${matching.size}"
        }
        matching.single().deliver(entry)
    }
}

class IntegrationTransportPlanner(targets: Collection<String>) {
    private val configuredTargets = targets.map(String::trim).filter(String::isNotEmpty).distinct()

    init {
        require(configuredTargets.isNotEmpty()) {
            "At least one integration message transport must be configured"
        }
    }

    fun targets(): List<String> = configuredTargets
}
