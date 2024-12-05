package com.jstore.common.utils.logging

class LogException: RuntimeException {

    constructor(message: String): super(message)
    constructor(cause: Throwable): super(cause)
    constructor(message: String, cause: Throwable): super(message, cause)
    constructor(message: String, cause: Throwable, enableSuppression: Boolean, writableStackTrace: Boolean): super(message, cause, enableSuppression, writableStackTrace)
}

abstract class Data {

}

class Factor<T: Data> {

}