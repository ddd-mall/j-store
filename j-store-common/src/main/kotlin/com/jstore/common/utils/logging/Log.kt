package com.jstore.common.utils.logging

interface Log {
    fun <T> info(format: String, args: Array<T>)
    fun <T> info(format: String, arg: T)
    fun info(format: String, throwable: Throwable)
    fun info(msg: String)

    fun debug(msg: String)
    fun debug(format: String, arg: Any)
    fun debug(format: String, throwable: Throwable)
    fun <T> debug(format: String, args: Array<T>)

    fun war(msg: String)
    fun war(format: String, arg: Any)
    fun war(format: String, throwable: Throwable)
    fun <T> war(format: String, args: Array<T>)

    fun error(msg: String)
    fun error(format: String, arg: Any)
    fun error(format: String, throwable: Throwable)
    fun <T> error(format: String, args: Array<T>)


}