package com.jstore.user.domain.useraccount.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.user.domain.useraccount.UserId
import java.time.LocalDateTime

/**
 * 用户账号登录事件
 */
data class UserAccountLoggedInEvent(
    override val source: Any,
    val userId: UserId,
    val loginTime: LocalDateTime,
) : DomainEvent
