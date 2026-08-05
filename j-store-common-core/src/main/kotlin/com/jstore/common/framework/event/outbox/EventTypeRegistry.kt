package com.jstore.common.framework.event.outbox

import com.jstore.common.framework.event.DomainEvent
import java.util.concurrent.ConcurrentHashMap

data class EventTypeKey(
    val eventName: String,
    val eventVersion: Int,
)

interface EventTypeRegistry {
    fun register(eventName: String, eventVersion: Int, eventClass: Class<out DomainEvent>)

    fun resolve(eventName: String, eventVersion: Int): Class<out DomainEvent>
}

class InMemoryEventTypeRegistry : EventTypeRegistry {
    private val eventTypes = ConcurrentHashMap<EventTypeKey, Class<out DomainEvent>>()

    override fun register(
        eventName: String,
        eventVersion: Int,
        eventClass: Class<out DomainEvent>,
    ) {
        val key = EventTypeKey(eventName, eventVersion)
        val existing = eventTypes.putIfAbsent(key, eventClass)
        require(existing == null || existing == eventClass) {
            "Duplicate @DomainEventType registration: eventName=$eventName, eventVersion=$eventVersion, " +
                "existingClass=${existing?.name}, duplicateClass=${eventClass.name}"
        }
    }

    override fun resolve(eventName: String, eventVersion: Int): Class<out DomainEvent> {
        eventTypes[EventTypeKey(eventName, eventVersion)]?.let {
            return it
        }

        throw OutboxSerializationException(
            "无法识别的事件类型: eventName=$eventName, eventVersion=$eventVersion"
        )
    }
}
