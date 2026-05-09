package com.jstore.common.utils

import java.io.Closeable


interface Lock : Closeable {
    fun unlock(): Result<Boolean, Throwable>
}