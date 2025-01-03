package com.jstore.common.framework

interface DomainEvent {
    fun topic() : String
}