package com.jstore.common.utils.persistent.jpa

import com.jstore.common.utils.persistent.SnowFlakSequence

open class DefaultIdentifierGenerator {
    private val sequence: SnowFlakSequence
    constructor() {
        this.sequence = SnowFlakSequence.SnowFlakSequence()
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