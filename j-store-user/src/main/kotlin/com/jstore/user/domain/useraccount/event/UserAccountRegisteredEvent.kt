package com.jstore.user.domain.useraccount.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.UserId

/**
 * 用户账号注册事件
 */
data class UserAccountRegisteredEvent(
    override val source: Any,
    val userId: UserId,
    val phoneNumber: PhoneNumber,
) : DomainEvent
