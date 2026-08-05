package com.jstore.common.properties

import com.jstore.common.framework.Identifier

open class Id<T>(open val value: T) : Identifier {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other != null && this::class == other::class && other is Id<*> && value == other.value)

    override fun hashCode(): Int = 31 * this::class.hashCode() + value.hashCode()

    override fun toString(): String = value.toString()
}
