package com.jstore.common.framework

import com.jstore.common.properties.Id

interface DomainEvent : Entity<DomainEventId> {
    fun topic() : String
    var id: DomainEventId?
    override fun id(): DomainEventId? = id
}


class  DomainEventId(override val value: Long): Id<Long>(value)