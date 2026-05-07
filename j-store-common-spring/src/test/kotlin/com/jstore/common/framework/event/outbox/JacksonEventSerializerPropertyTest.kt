package com.jstore.common.framework.event.outbox

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.event.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.time.Instant

/**
 * Feature: transactional-outbox, Property 2: 序列化/反序列化 Round-Trip
 *
 * Validates: Requirements 4.Ff, 4.2, 4.3, 1.5
 */
class JacksonEventSerializerPropertyTest : FunSpec({

    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    val eventTypeRegistry = InMemoryEventTypeRegistry().also { registry ->
        registry.register("order.created", 1, OrderCreatedEvent::class.java)
        registry.register("order.paid", 1, OrderPaidEvent::class.java)
        registry.register("order.shipped", 1, OrderShippedEvent::class.java)
        registry.register("order.completed", 1, OrderCompletedEvent::class.java)
        registry.register("order.cancelled", 1, OrderCancelledEvent::class.java)
    }
    val serializer = JacksonEventSerializer(objectMapper, eventTypeRegistry)

    // -- Generators --

    val arbOrderId = Arb.long(1L..Long.MAX_VALUE).map { OrderId(it) }
    val arbPrice = Arb.long(0L..10_000_000L).map { Price.ofFen(it) }
    val arbInstant = Arb.long(0L..4_000_000_000L).map { Instant.ofEpochSecond(it) }
    val arbItemSnapshot = Arb.bind(
        Arb.long(1L..100_000L),
        Arb.int(1..999)
    ) { skuId, qty -> OrderItemSnapshot(skuId, qty) }
    val arbItemList = Arb.list(arbItemSnapshot, 1..5)

    val arbOrderCreatedEvent: Arb<DomainEvent> = Arb.bind(
        arbOrderId, arbPrice, arbItemList, arbInstant
    ) { id, amount, items, ts -> OrderCreatedEvent(id, amount, items, ts) }

    val arbOrderPaidEvent: Arb<DomainEvent> = Arb.bind(
        arbOrderId, arbPrice, arbItemList, arbInstant
    ) { id, amount, items, ts -> OrderPaidEvent(id, amount, items, ts) }

    val arbOrderShippedEvent: Arb<DomainEvent> = Arb.bind(
        arbOrderId, arbInstant
    ) { id, ts -> OrderShippedEvent(id, ts) }

    val arbOrderCompletedEvent: Arb<DomainEvent> = Arb.bind(
        arbOrderId, arbInstant
    ) { id, ts -> OrderCompletedEvent(id, ts) }

    val arbOrderCancelledEvent: Arb<DomainEvent> = Arb.bind(
        arbOrderId, Arb.string(0..50), arbInstant
    ) { id, reason, ts -> OrderCancelledEvent(id, reason, ts) }

    val arbDomainEvent: Arb<DomainEvent> = Arb.choice(
        arbOrderCreatedEvent,
        arbOrderPaidEvent,
        arbOrderShippedEvent,
        arbOrderCompletedEvent,
        arbOrderCancelledEvent
    )

    test("Property 2: serialize then deserialize produces equivalent object") {
        checkAll(PropTestConfig(iterations = 20), arbDomainEvent) { event ->
            val json = serializer.serialize(event)
            val metadata = event.metadata
            val restored = serializer.deserialize(json, metadata.eventName, metadata.eventVersion)
            restored shouldBe event
        }
    }
})
