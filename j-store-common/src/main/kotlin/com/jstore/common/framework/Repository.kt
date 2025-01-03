package com.jstore.common.framework

import com.jstore.com.jstore.framework.Entity
import com.jstore.com.jstore.framework.Identify

interface Repository<I : Identify, E : Entity<I>> {
    fun save(entity: E): E
    fun findById(id: I): E?

}