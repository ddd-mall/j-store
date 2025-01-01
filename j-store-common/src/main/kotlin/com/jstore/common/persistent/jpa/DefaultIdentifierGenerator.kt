package com.jstore.common.persistent.jpa

import com.jstore.common.persistent.SnowFlakSequence

open class DefaultIdentifierGenerator {
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


}