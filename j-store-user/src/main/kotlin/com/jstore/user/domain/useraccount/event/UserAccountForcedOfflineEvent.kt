package com.jstore.user.domain.useraccount.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.user.domain.useraccount.UserId
import java.time.LocalDateTime

/**
 * 用户账号强制下线事件
 */
data class UserAccountForcedOfflineEvent(
    override val source: Any,
    val userId: UserId,
    val operationTime: LocalDateTime,
) : DomainEvent
