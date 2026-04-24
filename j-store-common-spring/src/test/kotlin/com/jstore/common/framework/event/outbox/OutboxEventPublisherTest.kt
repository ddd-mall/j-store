package com.jstore.common.framework.event.outbox

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderItemSnapshot
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.mockito.kotlin.*
import java.time.Instant

/**
 * OutboxEventPublisher 单元测试
 *
 * Validates: Requirements 1.1, 1.2, 1.3
 */
class OutboxEventPublisherTest : FunSpec({

    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    test("publishEvent creates OutboxEntry with correct fields and calls save") {
        val mockRepository = mock<OutboxEntryRepository> {
            on { save(any()) } doAnswer { it.arguments[0] as OutboxEntry }
        }
        val serializer = JacksonEventSerializer(objectMapper)
        val publisher = OutboxEventPublisher(mockRepository, serializer)

        val event = OrderCreatedEvent(
            orderId = OrderId(42L),
            totalAmount = Price.ofFen(9999),
            items = listOf(OrderItemSnapshot(1L, 2)),
            occurredAt = Instant.parse("2025-01-01T00:00:00Z")
        )

        publisher.publishEvent(event)

        val captor = argumentCaptor<OutboxEntry>()
        verify(mockRepository, times(1)).save(captor.capture())

        val saved = captor.firstValue
        saved.id shouldNotBe ""
        saved.eventType shouldBe "com.jstore.order.domain.order.event.OrderCreatedEvent"
        saved.status shouldBe OutboxEntryStatus.PENDING
        saved.retryCount shouldBe 0
        saved.payload shouldNotBe ""
    }

    test("serialization failure propagates exception ensuring business transaction rollback") {
        val mockRepository = mock<OutboxEntryRepository>()
        val mockSerializer = mock<EventSerializer> {
            on { serialize(any()) } doThrow RuntimeException("serialization error")
        }

        val publisher = OutboxEventPublisher(mockRepository, mockSerializer)

        val event = OrderCreatedEvent(
            orderId = OrderId(1L),
            totalAmount = Price.ofFen(100),
            items = listOf(OrderItemSnapshot(1L, 1)),
            occurredAt = Instant.now()
        )

        shouldThrow<RuntimeException> {
            publisher.publishEvent(event)
        }

        verify(mockRepository, never()).save(any())
    }
})
