package com.jstore.user.domain.useraccount.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.UserId
import java.time.Instant

@DomainEventType(name = "user.account-registered", version = 1)
data class UserAccountRegisteredEvent(
    val userId: UserId,
    val phoneNumber: PhoneNumber,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "user.account-registered"
    override val eventVersion = 1
    override val aggregateType = "UserAccount"
    override val aggregateId = userId.value.toString()
}
