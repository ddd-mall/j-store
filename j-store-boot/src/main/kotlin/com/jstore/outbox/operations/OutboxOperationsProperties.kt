package com.jstore.outbox.operations

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jstore.outbox.operations")
data class OutboxOperationsProperties(
    val adminUserIds: Set<Long> = emptySet(),
) {
    init {
        require(adminUserIds.all { it > 0 }) {
            "jstore.outbox.operations.admin-user-ids must contain only positive user IDs"
        }
    }

    fun isAdministrator(userId: Long): Boolean = userId in adminUserIds
}
