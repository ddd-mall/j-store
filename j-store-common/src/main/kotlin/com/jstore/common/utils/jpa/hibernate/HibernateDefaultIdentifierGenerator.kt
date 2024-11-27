package com.jstore.common.utils.jpa.hibernate

import com.jstore.common.utils.jpa.DefaultIdentifierGenerator
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator

class HibernateDefaultIdentifierGenerator: DefaultIdentifierGenerator(), IdentifierGenerator {

    override fun generate(session: SharedSessionContractImplementor?, `object`: Any?): Any {
        TODO("Not yet implemented")
    }
}