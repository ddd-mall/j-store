package com.jstore.common.framework.event.outbox

data class UpcastedEventPayload(
    val eventName: String,
    val eventVersion: Int,
    val payload: String,
)

interface EventUpcaster {
    val eventName: String
    val sourceVersion: Int
    val targetVersion: Int

    fun upcast(payload: String): String
}

interface EventUpcasterRegistry {
    fun register(upcaster: EventUpcaster)

    fun upcast(eventName: String, eventVersion: Int, payload: String): UpcastedEventPayload
}

class InMemoryEventUpcasterRegistry(
    upcasters: Iterable<EventUpcaster> = emptyList(),
) : EventUpcasterRegistry {
    private val upcasters = linkedMapOf<EventTypeKey, EventUpcaster>()

    init {
        upcasters.forEach(::register)
    }

    override fun register(upcaster: EventUpcaster) {
        require(upcaster.targetVersion > upcaster.sourceVersion) {
            "Event upcaster targetVersion must be greater than sourceVersion: eventName=${upcaster.eventName}, " +
                "sourceVersion=${upcaster.sourceVersion}, targetVersion=${upcaster.targetVersion}"
        }
        val key = EventTypeKey(upcaster.eventName, upcaster.sourceVersion)
        val existing = upcasters[key]
        require(existing == null) {
            "Duplicate EventUpcaster registration: eventName=${upcaster.eventName}, " +
                "sourceVersion=${upcaster.sourceVersion}, existing=${existing!!::class.java.name}, " +
                "duplicate=${upcaster::class.java.name}"
        }
        upcasters[key] = upcaster
    }

    override fun upcast(eventName: String, eventVersion: Int, payload: String): UpcastedEventPayload {
        var currentVersion = eventVersion
        var currentPayload = payload

        while (true) {
            val upcaster = upcasters[EventTypeKey(eventName, currentVersion)] ?: break
            currentPayload = upcaster.upcast(currentPayload)
            currentVersion = upcaster.targetVersion
        }

        return UpcastedEventPayload(
            eventName = eventName,
            eventVersion = currentVersion,
            payload = currentPayload,
        )
    }
}
