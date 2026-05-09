package com.jstore.user.domain.useraccount

import com.jstore.common.properties.Id

data class UserId(override val value: Long) : Id<Long>(value)
