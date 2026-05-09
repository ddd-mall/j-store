package com.jstore.common.framework

interface Page<T> {
    fun current(): Int
    fun size(): Int
    fun record(): Collection<T>
}

class SortedPage<T>(private val current: Int, private val size: Int, private val record: List<T>) : Page<T> {


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