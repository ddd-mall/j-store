package com.jstore.authentication.context

import com.jstore.user.domain.useraccount.UserId

object AuthenticatedUserContext {
    private val holder: ThreadLocal<UserId> = ThreadLocal()

    fun set(userId: UserId) {
        holder.set(userId)
    }

    fun getCurrentUserId(): UserId =
        holder.get() ?: throw AuthenticationException("当前上下文中无已认证用户")

    fun getCurrentUserIdOrNull(): UserId? = holder.get()

    fun clear() {
        holder.remove()
    }
}
