package com.jstore.common.framework.event.outbox

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.LocalDomainEventBus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LocalDomainEventDeliveryChannelTest :
    FunSpec({
        test("local domain channel deserializes and synchronously publishes the domain event") {
            val event = StubDomainEvent()
            val serializer = mock<EventSerializer>()
            val bus = mock<LocalDomainEventBus>()
            whenever(serializer.deserialize("{}", "order.created", 2)).thenReturn(event)
            val channel = LocalDomainEventDeliveryChannel(serializer, bus)
            val entry =
                OutboxEntry(
                    id = "entry-1",
                    eventType = "order.created",
                    payload = "{}",
                    aggregateType = "Order",
                    aggregateId = "1",
                    status = OutboxEntryStatus.PENDING,
                    createdAt = Instant.parse("2026-08-05T00:00:00Z"),
                    updatedAt = Instant.parse("2026-08-05T00:00:00Z"),
                    eventVersion = 2,
                )

            channel.deliver(entry)

            channel.target shouldBe OutboxDeliveryTarget.LOCAL_DOMAIN
            verify(bus).publishEvent(event)
        }
    })

private data class StubDomainEvent(override val source: Any = "stub") : DomainEvent
