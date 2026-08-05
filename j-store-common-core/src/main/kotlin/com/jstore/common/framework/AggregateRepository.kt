package com.jstore.common.framework

/** Persistence port for an aggregate consistency boundary. */
interface AggregateRepository<I : Identifier, A : AggregateRoot<I>> {
    fun save(aggregate: A): A

    fun findById(id: I): A?
}
