package com.jstore.common.persistent.jpa.hibernate

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.persistent.jpa.DefaultIdentifierGenerator
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator

class HibernateDefaultIdentifierGenerator: DefaultIdentifierGenerator, IdentifierGenerator {
    constructor(): super()
    constructor(sequence: SnowFlakSequence): super(sequence)
    constructor(workerId: Long, datacenterId: Long): super(workerId, datacenterId)

    override fun generate(session: SharedSessionContractImplementor?, `object`: Any?): Any {
        return nextId()
    }
}