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

import com.jstore.outbox.IntegrationDeliveryRoute
import com.jstore.outbox.IntegrationRoute
import com.jstore.outbox.OutboxTransportIds
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jstore.messaging")
data class MessagingProperties(
    val targets: Set<String> = setOf(OutboxTransportIds.LOCAL),
    val routes: List<MessagingRouteProperties> = emptyList(),
) {
    init {
        require(targets.isNotEmpty() || routes.isNotEmpty()) {
            "jstore.messaging.targets and routes must not both be empty"
        }
        require(targets.all { it.isNotBlank() }) {
            "jstore.messaging.targets must contain only non-blank transport IDs"
        }
        require(OutboxTransportIds.LOCAL_DOMAIN !in targets) {
            "local-domain is reserved for domain events"
        }
        require(routes.map { it.logicalDestination }.distinct().size == routes.size) {
            "jstore.messaging.routes logical destinations must be unique"
        }
        require(
            routes
                .flatMap { it.deliveries }
                .none {
                    it.transportId == OutboxTransportIds.LOCAL_DOMAIN
                }
        ) {
            "local-domain is reserved for domain events"
        }
    }

    fun integrationRoutes(): List<IntegrationRoute> = routes.map { route ->
        IntegrationRoute(
            logicalDestination = route.logicalDestination,
            deliveries =
                route.deliveries.map {
                    IntegrationDeliveryRoute(
                        transportId = it.transportId,
                        destination = it.destination,
                        deliveryProfile = it.deliveryProfile,
                    )
                },
        )
    }
}

data class MessagingRouteProperties(
    val logicalDestination: String = "",
    val deliveries: List<MessagingDeliveryProperties> = emptyList(),
)

data class MessagingDeliveryProperties(
    val transportId: String = "",
    val destination: String = "",
    val deliveryProfile: String = "STANDARD",
)
