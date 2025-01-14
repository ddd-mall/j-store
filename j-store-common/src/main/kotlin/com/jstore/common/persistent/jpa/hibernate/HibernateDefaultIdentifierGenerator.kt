package com.jstore.common.persistent.jpa.hibernate

import com.jstore.common.persistent.SnowFlakSequence
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator


class HibernateDefaultIdentifierGenerator : IdentifierGenerator {
    private val sequence: SnowFlakSequence
    constructor() {
        this.sequence = SnowFlakSequence()
    }
    constructor(sequence: SnowFlakSequence) {
        this.sequence = sequence
    }

    constructor(workerId: Long, datacenterId: Long) {
        this.sequence = SnowFlakSequence(workerId, datacenterId)
    }

    fun nextId(): Long {
        return sequence.nextId()
    }

    override fun generate(session: SharedSessionContractImplementor?, `object`: Any?): Any {
        return nextId()
    }
}