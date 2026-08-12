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

data class IntegrationDeliveryRoute(
    val transportId: String,
    val destination: String,
    val deliveryProfile: String = "STANDARD",
) {
    init {
        require(transportId.isNotBlank()) { "Route transportId must not be blank" }
        require(destination.isNotBlank()) { "Route destination must not be blank" }
        require(deliveryProfile.isNotBlank()) { "Route deliveryProfile must not be blank" }
    }
}

data class IntegrationRoute(
    val logicalDestination: String,
    val deliveries: List<IntegrationDeliveryRoute>,
) {
    init {
        require(logicalDestination.isNotBlank()) { "Route logicalDestination must not be blank" }
        require(deliveries.isNotEmpty()) { "Route must contain at least one delivery" }
        require(deliveries.map { it.transportId }.distinct().size == deliveries.size) {
            "Route deliveries must use distinct transport IDs: $logicalDestination"
        }
    }
}

data class IntegrationPublication(
    val transportId: String,
    val logicalDestination: String,
    val destination: String,
    val deliveryProfile: String,
)

/** Resolves a logical bounded-context destination into independently tracked deliveries. */
class IntegrationPublicationPlanner(
    defaultTargets: Collection<String>,
    routes: Collection<IntegrationRoute> = emptyList(),
) {
    private val configuredDefaultTargets =
        defaultTargets.map(String::trim).filter(String::isNotEmpty).distinct().sorted()
    private val configuredRoutes: Map<String, IntegrationRoute>

    init {
        val normalizedRoutes = routes.toList()
        require(
            normalizedRoutes.map { it.logicalDestination }.distinct().size == normalizedRoutes.size
        ) {
            "Integration routes must use distinct logical destinations"
        }
        require(configuredDefaultTargets.isNotEmpty() || normalizedRoutes.isNotEmpty()) {
            "At least one integration message transport or route must be configured"
        }
        configuredRoutes = normalizedRoutes.associateBy { it.logicalDestination }
    }

    fun plan(logicalDestination: String): List<IntegrationPublication> {
        require(logicalDestination.isNotBlank()) { "logicalDestination must not be blank" }
        val route = configuredRoutes[logicalDestination]
        if (route != null) {
            return route.deliveries.map {
                IntegrationPublication(
                    transportId = it.transportId,
                    logicalDestination = route.logicalDestination,
                    destination = it.destination,
                    deliveryProfile = it.deliveryProfile,
                )
            }
        }
        check(configuredDefaultTargets.isNotEmpty()) {
            "No integration route or default transport configured for $logicalDestination"
        }
        return configuredDefaultTargets.map {
            IntegrationPublication(
                transportId = it,
                logicalDestination = logicalDestination,
                destination = logicalDestination,
                deliveryProfile = "STANDARD",
            )
        }
    }

    fun requiredTransportIds(): Set<String> =
        (configuredDefaultTargets +
                configuredRoutes.values.flatMap { route ->
                    route.deliveries.map { it.transportId }
                })
            .toSet()
}
