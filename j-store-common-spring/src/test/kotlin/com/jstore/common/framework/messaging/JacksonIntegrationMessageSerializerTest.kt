package com.jstore.common.framework.messaging

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.jstore.common.framework.event.outbox.InMemoryIntegrationMessageTypeRegistry
import com.jstore.contracts.commerce.ContractAuthorizedSaleItem
import com.jstore.contracts.commerce.ReserveInventoryCommand
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class JacksonIntegrationMessageSerializerTest :
    FunSpec({
        test("portable integration contract round trips by stable name and version") {
            val registry = InMemoryIntegrationMessageTypeRegistry()
            registry.register("inventory.reserve", 3, ReserveInventoryCommand::class.java)
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
