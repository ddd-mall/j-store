package com.jstore.common.utils.persistent.jpa.hibernate

import com.jstore.common.utils.persistent.SnowFlakSequence
import com.jstore.common.utils.persistent.jpa.DefaultIdentifierGenerator
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator

class HibernateDefaultIdentifierGenerator: DefaultIdentifierGenerator, IdentifierGenerator {
    constructor() {
        DefaultIdentifierGenerator()
    }
    constructor(sequence: SnowFlakSequence) {
        DefaultIdentifierGenerator(sequence)
    }
    constructor(workerId: Long, datacenterId: Long) {
        DefaultIdentifierGenerator(workerId, datacenterId)
    }

    override fun generate(session: SharedSessionContractImplementor?, `object`: Any?): Any {
        return nextId()
    }
}