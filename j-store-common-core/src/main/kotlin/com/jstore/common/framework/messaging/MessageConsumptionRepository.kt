package com.jstore.common.framework.messaging

import com.jstore.common.framework.event.DomainEvent

/** Atomic inbox/idempotency port shared by local and broker-delivered messages. */
fun interface MessageConsumptionRepository {
    fun tryStart(
        consumerId: String,
        messageId: String,
        messageName: String,
        messageVersion: Int,
    ): Boolean
}

fun MessageConsumptionRepository.tryStart(consumerId: String, event: DomainEvent): Boolean =
    tryStart(
        consumerId = consumerId,
        messageId = event.eventId,
        messageName = event.eventName,
        messageVersion = event.eventVersion,
    )
