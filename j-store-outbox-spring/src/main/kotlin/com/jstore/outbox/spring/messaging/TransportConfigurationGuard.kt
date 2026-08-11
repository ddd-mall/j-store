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

import com.jstore.messaging.*
import com.jstore.outbox.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton

class TransportConfigurationGuard(
    private val properties: MessagingProperties,
    private val localChannels: List<OutboxDeliveryChannel>,
    private val transportProvider: ObjectProvider<IntegrationMessageTransport>,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        val transportIds =
            localChannels.map { it.transportId } +
                transportProvider.orderedStream().map { it.transportId }.toList()
        val requiredTransportIds = properties.targets + OutboxTransportIds.LOCAL_DOMAIN
        requiredTransportIds.forEach { transportId ->
            val matches = transportIds.count { it == transportId }
            check(matches == 1) {
                "Outbox requires exactly one delivery channel for " +
                    "transportId=$transportId, found=$matches"
            }
        }
    }
}
