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
import com.jstore.contracts.commerce.ContractAddressComponent
import com.jstore.contracts.commerce.ContractAuthenticatedAccount
import com.jstore.contracts.commerce.ContractAuthorizedSaleItem
import com.jstore.contracts.commerce.ContractPaymentAllocation
import com.jstore.contracts.commerce.ContractRecipient
import com.jstore.contracts.commerce.ContractShippingAddress
import com.jstore.contracts.commerce.ContractTradeOrderItem
import com.jstore.contracts.commerce.CreateOrderFromTradeIntegrationCommand
import com.jstore.contracts.commerce.InventoryReservedIntegrationEvent
import com.jstore.contracts.commerce.PaymentCancellationConfirmedIntegrationEvent
import com.jstore.contracts.commerce.PreparePaymentInstallmentCommand
import com.jstore.contracts.commerce.ReserveInventoryCommand
import com.jstore.outbox.InMemoryIntegrationMessageTypeRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.Instant

class JacksonIntegrationMessageSerializerTest :
    FunSpec({
        test("payment cancellation confirmation round trips with trade and installment identity") {
            val registry = InMemoryIntegrationMessageTypeRegistry()
            registry.register(
                "payment.installment.cancellation-confirmed",
                1,
                PaymentCancellationConfirmedIntegrationEvent::class.java,
            )
            val serializer =
                JacksonIntegrationMessageSerializer(
                    JsonMapper.builder()
                        .addModule(KotlinModule.Builder().build())
                        .addModule(JavaTimeModule())
                        .build(),
                    registry,
                )
            val occurredAt = Instant.parse("2026-08-05T00:00:00Z")
            val event =
                PaymentCancellationConfirmedIntegrationEvent(
                    42,
                    4201,
                    "FULL",
                    8001,
                    "buyer cancelled",
                    "cancel-command",
                    occurredAt,
                )

            serializer.deserialize(
                serializer.serialize(event),
                event.messageName,
                event.messageVersion,
            ) shouldBe event
        }

        test("payment preparation command round trips with frozen allocation and deadlines") {
            val registry = InMemoryIntegrationMessageTypeRegistry()
            registry.register(
                "payment.installment.prepare",
                1,
                PreparePaymentInstallmentCommand::class.java,
            )
            val serializer =
                JacksonIntegrationMessageSerializer(
                    JsonMapper.builder()
                        .addModule(KotlinModule.Builder().build())
                        .addModule(JavaTimeModule())
                        .build(),
                    registry,
                )
            val occurredAt = Instant.parse("2026-08-05T00:00:00Z")
            val command =
                PreparePaymentInstallmentCommand(
                    42,
                    4201,
                    "FULL",
                    1000,
                    "CNY",
                    listOf(ContractPaymentAllocation(11, 21, 7, 1000)),
                    "trade-42",
                    occurredAt,
                    occurredAt.plusSeconds(600),
                    occurredAt.plusSeconds(900),
                )

            val restored =
                serializer.deserialize(
                    serializer.serialize(command),
                    command.messageName,
                    command.messageVersion,
                )

            restored shouldBe command
        }

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

            val payload = serializer.serialize(command)
            payload shouldContain "\"merchantScopeId\":\"7\""
            payload shouldNotContain "\"tenantId\""
            command.metadata.merchantScopeId shouldBe "7"
            command.metadata.deploymentScopeId shouldBe null

            val restored =
                serializer.deserialize(
                    payload,
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

        test("trade order creation command round trips without domain value objects") {
            val registry = InMemoryIntegrationMessageTypeRegistry()
            registry.register(
                "order.create-from-trade",
                2,
                CreateOrderFromTradeIntegrationCommand::class.java,
            )
            val serializer =
                JacksonIntegrationMessageSerializer(
                    JsonMapper.builder()
                        .addModule(KotlinModule.Builder().build())
                        .addModule(JavaTimeModule())
                        .build(),
                    registry,
                )
            val command =
                CreateOrderFromTradeIntegrationCommand(
                    tradeId = 42,
                    orderPlanId = 4201,
                    planDigest = "sha256:plan-4201",
                    merchantId = 7,
                    buyer = ContractAuthenticatedAccount("issuer-a", 8),
                    buyerName = "buyer",
                    buyerPhone = "13800000000",
                    recipient =
                        ContractRecipient(
                            "recipient",
                            "13900000000",
                            "recipient@example.com",
                            "CN",
                            "110101",
                            "No. 1 Road",
                            "100000",
                            mapOf("idType" to "passport"),
                        ),
                    shippingAddress =
                        ContractShippingAddress(
                            "CN",
                            listOf(
                                ContractAddressComponent(
                                    "110000",
                                    1,
                                    "province",
                                    mapOf("zh-CN" to "北京市"),
                                    "zh-CN",
                                )
                            ),
                        ),
                    items =
                        listOf(
                            ContractTradeOrderItem(
                                10,
                                11,
                                12,
                                7,
                                2,
                                "CN-NORTH-1",
                                "WEB",
                                "goods",
                                "red",
                                2,
                                500,
                                3,
                            )
                        ),
                    payableAmountFen = 1000,
                    currency = "CNY",
                    sourceMessageId = "inventory-reserved-42",
                    occurredAtValue = Instant.parse("2026-08-05T00:00:00Z"),
                )

            val restored =
                serializer.deserialize(
                    serializer.serialize(command),
                    command.messageName,
                    command.messageVersion,
                )

            restored shouldBe command
        }
    })
