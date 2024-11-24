package com.jstore.order.common

import com.jstore.common.framework.Page

data class MockPage<T>(
    val current: Int,
    val size: Int,
    var record: List<T>,

): Page<T> {
    override fun current(): Int {
        return current
    }

    override fun size(): Int {
        return size
    }

    override fun record(): Collection<T> {
        return record
    }
}