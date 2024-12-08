package com.jstore.common.logging

interface Log {
    fun isDebugEnabled(): Boolean
    fun info(format: String, args: Array<out Any>)
    fun info(format: String, arg: Any)
    fun info(format: String, throwable: Throwable)
    fun info(msg: String)

    fun debug(msg: String)
    fun debug(format: String, arg: Any)
    fun debug(format: String, throwable: Throwable)
    fun debug(format: String, args: Array<out Any>)

    fun warn(msg: String)
    fun warn(format: String, arg: Any)
    fun warn(format: String, throwable: Throwable)
    fun warn(format: String, args: Array<out Any>)

    fun error(msg: String)
    fun error(format: String, arg: Any)
    fun error(format: String, throwable: Throwable)
    fun error(format: String, args: Array<out Any>)


}