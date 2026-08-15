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

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.jstore.contracts.commerce.ContractAuthorizedSaleItem
import com.jstore.contracts.commerce.InventoryReservedIntegrationEvent
import com.jstore.contracts.commerce.ReserveInventoryCommand
import com.jstore.outbox.InMemoryIntegrationMessageTypeRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class JacksonIntegrationMessageSerializerTest :
    FunSpec({
        test("portable integration contract round trips by stable name and version") {
            val registry = InMemoryIntegrationMessageTypeRegistry()
            registry.register("inventory.reserve", 1, ReserveInventoryCommand::class.java)
            val serializer =
                JacksonIntegrationMessageSerializer(
                    JsonMapper.builder()
                        .addModule(KotlinModule.Builder().build())
                        .addModule(JavaTimeModule())
                        .build(),
                    registry,
                )
            val command =
                ReserveInventoryCommand(
                    42,
                    4201,
                    listOf(
                        ContractAuthorizedSaleItem(
                            authorizationId = "auth-42",
                            offerId = 7001,
                            skuId = 1001,
                            quantity = 2,
                            fulfillmentNodeId = "CN-NORTH-1",
                            expiresAt = Instant.parse("2026-08-05T00:15:00Z"),
                        )
                    ),
                    "order-created-42",
                    7,
                    Instant.parse("2026-08-05T00:00:00Z"),
                    Instant.parse("2026-08-05T00:10:00Z"),
                )

            val restored =
                serializer.deserialize(
                    serializer.serialize(command),
                    command.messageName,
                    command.messageVersion,
                )

            restored shouldBe command
        }

        test("inventory reserved fact round trips with its required expiry") {
            val registry = InMemoryIntegrationMessageTypeRegistry()
            registry.register(
                "inventory.reserved",
                1,
                InventoryReservedIntegrationEvent::class.java,
            )
            val serializer =
                JacksonIntegrationMessageSerializer(
                    JsonMapper.builder()
                        .addModule(KotlinModule.Builder().build())
                        .addModule(JavaTimeModule())
                        .build(),
                    registry,
                )

            val event =
                InventoryReservedIntegrationEvent(
                    tradeId = 42,
                    orderPlanId = 4201,
                    authorizationIds = listOf("auth-42"),
                    reservationIds = listOf("reservation-42"),
                    reservationExpiresAt = Instant.parse("2026-08-05T00:15:00Z"),
                    sourceMessageId = "stock-reserved-42",
                    occurredAtValue = Instant.parse("2026-08-05T00:00:00Z"),
                )

            val restored =
                serializer.deserialize(
                    serializer.serialize(event),
                    "inventory.reserved",
                    1,
                ) as InventoryReservedIntegrationEvent

            restored shouldBe event
        }
    })
