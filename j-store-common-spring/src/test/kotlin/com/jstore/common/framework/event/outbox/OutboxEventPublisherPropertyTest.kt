package com.jstore.common.framework.event.outbox

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.MerchantId
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.event.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.time.Instant
import org.mockito.kotlin.*

/**
 * Feature: transactional-outbox, Property 1: 事件持久化为 PENDING 状态
 *
 * Validates: Requirements 1.1
 */
class OutboxEventPublisherPropertyTest :
    FunSpec({
        val objectMapper =
            ObjectMapper()
                .registerKotlinModule()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        val realSerializer = JacksonEventSerializer(objectMapper)

        // -- Generators --

        val arbOrderId = Arb.long(1L..Long.MAX_VALUE).map { OrderId(it) }
        val arbPrice = Arb.long(0L..10_000_000L).map { Price.ofFen(it) }
        val arbInstant = Arb.long(0L..4_000_000_000L).map { Instant.ofEpochSecond(it) }
        val arbItemSnapshot =
            Arb.bind(
                Arb.long(1L..100_000L),
                Arb.int(1..999),
            ) { skuId, qty ->
                OrderItemSnapshot(skuId, qty)
            }
        val arbItemList = Arb.list(arbItemSnapshot, 1..5)

        val arbDomainEvent: Arb<DomainEvent> =
            Arb.choice(
                Arb.bind(arbOrderId, arbPrice, arbItemList, arbInstant) { id, amount, items, ts ->
                    OrderCreatedEvent(id, MerchantId(7), amount, "CNY", items, ts) as DomainEvent
                },
                Arb.bind(arbOrderId, arbPrice, arbItemList, arbInstant) { id, amount, items, ts ->
                    OrderPaidEvent(id, MerchantId(7), "payment-1", amount, "CNY", items, ts)
                        as DomainEvent
                },
                Arb.bind(arbOrderId, arbInstant) { id, ts ->
                    OrderCompletedEvent(id, ts) as DomainEvent
                },
                Arb.bind(arbOrderId, Arb.string(0..50), arbInstant) { id, reason, ts ->
                    OrderCancelledEvent(id, reason, ts) as DomainEvent
                },
            )

        test("Property 1: publishEvent saves entry with PENDING status and stable envelope") {
            checkAll(PropTestConfig(iterations = 20), arbDomainEvent) { event ->
                val mockRepository =
                    mock<OutboxEntryRepository> {
                        on { save(any()) } doAnswer { it.arguments[0] as OutboxEntry }
                    }
                val eventTypeRegistry =
                    InMemoryEventTypeRegistry().apply {
                        val eventType = event::class.java.getAnnotation(DomainEventType::class.java)
                        register(
                            event.metadata.eventName,
                            event.metadata.eventVersion,
                            event::class.java,
                        )
                        eventType.name shouldBe event.metadata.eventName
                        eventType.version shouldBe event.metadata.eventVersion
                    }

                val publisher =
                    OutboxEventPublisher(
                        mockRepository,
                        realSerializer,
                        SnowFlakSequence(1, 1),
                        eventTypeRegistry,
                    )
                publisher.publishEvent(event)

                val captor = argumentCaptor<OutboxEntry>()
                verify(mockRepository).save(captor.capture())

                val savedEntry = captor.firstValue
                savedEntry.status shouldBe OutboxEntryStatus.PENDING
                savedEntry.eventId shouldBe event.metadata.eventId
                savedEntry.eventType shouldBe event.metadata.eventName
                savedEntry.eventClassName shouldBe event::class.java.name
                savedEntry.eventVersion shouldBe event.metadata.eventVersion
                savedEntry.occurredAt shouldBe event.metadata.occurredAt
            }
        }
    })
