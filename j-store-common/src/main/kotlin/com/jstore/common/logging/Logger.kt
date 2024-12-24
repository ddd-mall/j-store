package com.jstore.common.logging

interface Logger {
    fun isDebugEnabled(): Boolean
    fun info(msg: String)
    fun info(format: String, arg: Any)
    fun info(format: String, throwable: Throwable)
    fun info(format: String, vararg args: Any)

    fun debug(msg: String)
    fun debug(format: String, arg: Any)
    fun debug(format: String, throwable: Throwable)
    fun debug(format: String, vararg args: Any)

    fun warn(msg: String)
    fun warn(format: String, arg: Any)
    fun warn(format: String, throwable: Throwable)
    fun warn(format: String, vararg args: Any)

    fun error(msg: String)
    fun error(format: String, arg: Any)
    fun error(format: String, throwable: Throwable)
    fun error(format: String, vararg args: Any)
}