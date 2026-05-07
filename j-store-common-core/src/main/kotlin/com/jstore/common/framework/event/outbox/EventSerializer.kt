package com.jstore.common.framework.event.outbox

import com.jstore.common.framework.event.DomainEvent

/**
 * 事件序列化/反序列化接口。
 */
interface EventSerializer {

    fun serialize(event: DomainEvent): String

    fun deserialize(payload: String, eventName: String, eventVersion: Int): DomainEvent
}
