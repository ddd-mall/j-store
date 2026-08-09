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
package com.jstore.common.framework.messaging

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton

class BrokerTransportModeGuard(
    private val properties: MessagingProperties,
    private val transportProvider: ObjectProvider<BrokerIntegrationMessageTransport>,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        if (
            properties.mode == IntegrationMessagingMode.BROKER ||
                properties.mode == IntegrationMessagingMode.HYBRID
        ) {
            check(transportProvider.getIfAvailable() != null) {
                "jstore.messaging.mode=${properties.mode.name.lowercase()} requires exactly one " +
                    "BrokerIntegrationMessageTransport bean"
            }
            check(transportProvider.orderedStream().limit(2).count() == 1L) {
                "jstore.messaging.mode=${properties.mode.name.lowercase()} requires exactly one " +
                    "BrokerIntegrationMessageTransport bean"
            }
        }
    }
}
