package com.jstore.common.utils

interface Lock {
    fun isAcquire(): Boolean

    fun unlock(): Result<Boolean, Throwable>
}