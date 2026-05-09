package com.jstore.user.domain.useraccount.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import com.jstore.user.domain.useraccount.UserId
import java.time.Instant
import java.time.LocalDateTime

/**
 * 用户账号登录事件
 */
@DomainEventType(name = "user.account-logged-in", version = 1)
data class UserAccountLoggedInEvent(
    override val source: Any,
    val userId: UserId,
    val loginTime: LocalDateTime,
    override val occurredAt: Instant = Instant.now(),
) : ExplicitDomainEvent {
    override val eventName: String = "user.account-logged-in"
    override val eventVersion: Int = 1
    override val aggregateType: String = "UserAccount"
    override val aggregateId: String = userId.value.toString()
    override val eventId: String = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
