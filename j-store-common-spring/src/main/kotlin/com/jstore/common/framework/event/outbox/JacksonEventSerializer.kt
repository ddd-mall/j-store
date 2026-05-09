package com.jstore.common.framework.event.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.common.framework.event.DomainEvent

/**
 * 基于 Jackson 的事件序列化/反序列化实现。
 */
class JacksonEventSerializer(
    private val objectMapper: ObjectMapper,
    private val eventTypeRegistry: EventTypeRegistry = InMemoryEventTypeRegistry(),
    private val eventUpcasterRegistry: EventUpcasterRegistry = InMemoryEventUpcasterRegistry(),
) : EventSerializer {

    override fun serialize(event: DomainEvent): String {
        return objectMapper.writeValueAsString(event)
    }

    override fun deserialize(payload: String, eventName: String, eventVersion: Int): DomainEvent {
        val upcasted = eventUpcasterRegistry.upcast(eventName, eventVersion, payload)
        val clazz = eventTypeRegistry.resolve(upcasted.eventName, upcasted.eventVersion)
        return try {
            objectMapper.readValue(upcasted.payload, clazz) as DomainEvent
        } catch (e: Exception) {
            val summary = if (upcasted.payload.length > 200) upcasted.payload.substring(0, 200) + "..." else upcasted.payload
            throw OutboxSerializationException(
                "JSON 反序列化失败, eventName=${upcasted.eventName}, eventVersion=${upcasted.eventVersion}, payload=$summary", e
            )
        }
    }
}
