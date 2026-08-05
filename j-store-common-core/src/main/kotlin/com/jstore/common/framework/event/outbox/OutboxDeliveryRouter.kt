package com.jstore.common.framework.event.outbox

interface OutboxDeliveryChannel {
    val target: OutboxDeliveryTarget

    fun deliver(entry: OutboxEntry)
}

class OutboxDeliveryRouter(private val channels: List<OutboxDeliveryChannel>) {
    fun deliver(entry: OutboxEntry) {
        val matching = channels.filter { it.target == entry.deliveryTarget }
        check(matching.size == 1) {
            "Expected exactly one outbox delivery channel for target=${entry.deliveryTarget}, found=${matching.size}"
        }
        matching.single().deliver(entry)
    }
}
