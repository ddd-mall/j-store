package com.jstore.common.framework.messaging

import com.jstore.common.framework.event.outbox.InMemoryIntegrationMessageTypeRegistry
import com.jstore.common.framework.event.outbox.IntegrationMessageSerializer
import com.jstore.common.framework.event.outbox.IntegrationMessageType
import com.jstore.common.framework.event.outbox.OutboxDeliveryTarget
import com.jstore.common.framework.event.outbox.OutboxEntryRepository
import com.jstore.common.framework.event.outbox.OutboxMessageKind
import com.jstore.common.persistent.SnowFlakSequence
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OutboxIntegrationMessagePublisherTest :
    FunSpec({
        test("hybrid mode persists independently retryable local and broker publications") {
            val repository = mock<OutboxEntryRepository>()
            val serializer = mock<IntegrationMessageSerializer>()
            val registry = InMemoryIntegrationMessageTypeRegistry()
            registry.register("test.inventory.reserve", 1, TestReserveInventoryCommand::class.java)
            whenever(serializer.serialize(message)).thenReturn("{\"orderId\":42}")
            val publisher =
                OutboxIntegrationMessagePublisher(
                    repository,
                    serializer,
                    SnowFlakSequence(1, 1),
                    registry,
                    IntegrationPublicationPlanner(IntegrationMessagingMode.HYBRID),
                )

            publisher.publish(message)

            val captor = argumentCaptor<com.jstore.common.framework.event.outbox.OutboxEntry>()
            verify(repository, times(2)).save(captor.capture())
            captor.allValues
                .map { it.deliveryTarget }
                .shouldContainExactly(
                    OutboxDeliveryTarget.LOCAL_INTEGRATION,
                    OutboxDeliveryTarget.BROKER,
                )
            captor.allValues.map { it.eventId }.distinct() shouldBe listOf(message.messageId)
            captor.allValues.map { it.messageKind }.distinct() shouldBe
                listOf(OutboxMessageKind.INTEGRATION_COMMAND)
            captor.allValues.map { it.partitionKey }.distinct() shouldBe
                listOf(message.partitionKey)
            captor.allValues.map { it.correlationId }.distinct() shouldBe
                listOf(message.correlationId)
        }
    })

@IntegrationMessageType(name = "test.inventory.reserve", version = 1)
data class TestReserveInventoryCommand(
    val orderId: Long,
    override val occurredAt: Instant,
) : IntegrationCommand {
    override val messageId: String = "message-1"
    override val messageName: String = "test.inventory.reserve"
    override val messageVersion: Int = 1
    override val partitionKey: String = orderId.toString()
    override val correlationId: String = "checkout-42"
    override val causationId: String = "order-created-42"
    override val tenantId: String = "merchant-7"
    override val destination: String = "inventory.commands"
}

val message =
    TestReserveInventoryCommand(
        orderId = 42,
        occurredAt = Instant.parse("2026-08-05T00:00:00Z"),
    )
