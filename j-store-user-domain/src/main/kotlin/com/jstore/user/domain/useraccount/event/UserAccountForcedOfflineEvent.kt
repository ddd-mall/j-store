package com.jstore.user.domain.useraccount.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.user.domain.useraccount.UserId
import java.time.Instant
import java.time.LocalDateTime

@DomainEventType(name = "user.account-forced-offline", version = 1)
data class UserAccountForcedOfflineEvent(
    val userId: UserId,
    val operationTime: LocalDateTime,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "user.account-forced-offline"
    override val eventVersion = 1
    override val aggregateType = "UserAccount"
    override val aggregateId = userId.value.toString()
}
