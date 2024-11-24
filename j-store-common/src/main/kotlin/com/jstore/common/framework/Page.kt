package com.jstore.common.framework

interface Page<T> {
    fun current(): Int
    fun size(): Int
    fun record(): Collection<T>
}