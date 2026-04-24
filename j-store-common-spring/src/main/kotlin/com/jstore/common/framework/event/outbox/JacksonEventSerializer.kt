package com.jstore.common.framework.event.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.common.framework.event.DomainEvent

/**
 * 基于 Jackson 的事件序列化/反序列化实现。
 */
class JacksonEventSerializer(
    private val objectMapper: ObjectMapper
) : EventSerializer {

    override fun serialize(event: DomainEvent): String {
        return objectMapper.writeValueAsString(event)
    }

    override fun deserialize(payload: String, eventType: String): DomainEvent {
        val clazz = try {
            Class.forName(eventType)
        } catch (e: ClassNotFoundException) {
            throw OutboxSerializationException(
                "无法识别的事件类型: $eventType", e
            )
        }
        return try {
            objectMapper.readValue(payload, clazz) as DomainEvent
        } catch (e: Exception) {
            val summary = if (payload.length > 200) payload.substring(0, 200) + "..." else payload
            throw OutboxSerializationException(
                "JSON 反序列化失败, eventType=$eventType, payload=$summary", e
            )
        }
    }
}
