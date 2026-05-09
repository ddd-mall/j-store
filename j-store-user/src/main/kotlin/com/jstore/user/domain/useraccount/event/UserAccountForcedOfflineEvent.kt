package com.jstore.user.domain.useraccount.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import com.jstore.user.domain.useraccount.UserId
import java.time.Instant
import java.time.LocalDateTime

/**
 * 用户账号强制下线事件
 */
@DomainEventType(name = "user.account-forced-offline", version = 1)
data class UserAccountForcedOfflineEvent(
    override val source: Any,
    val userId: UserId,
    val operationTime: LocalDateTime,
    override val occurredAt: Instant = Instant.now(),
) : ExplicitDomainEvent {
    override val eventName: String = "user.account-forced-offline"
    override val eventVersion: Int = 1
    override val aggregateType: String = "UserAccount"
    override val aggregateId: String = userId.value.toString()
    override val eventId: String = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
