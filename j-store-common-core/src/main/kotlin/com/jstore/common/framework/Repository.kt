package com.jstore.common.framework


interface Repository<I : Identify, E : Entity<I>> {
    fun save(entity: E): E
    fun findById(id: I): E?
}