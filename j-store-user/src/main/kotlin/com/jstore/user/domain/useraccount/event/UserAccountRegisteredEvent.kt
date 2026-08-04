package com.jstore.user.domain.useraccount.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.UserId
import java.time.Instant

/** 用户账号注册事件 */
@DomainEventType(name = "user.account-registered", version = 1)
data class UserAccountRegisteredEvent(
    override val source: Any,
    val userId: UserId,
    val phoneNumber: PhoneNumber,
    override val occurredAt: Instant = Instant.now(),
) : ExplicitDomainEvent {
    override val eventName: String = "user.account-registered"
    override val eventVersion: Int = 1
    override val aggregateType: String = "UserAccount"
    override val aggregateId: String = userId.value.toString()
    override val eventId: String =
        stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
