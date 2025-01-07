package com.jstore.common.errors

class Errors: RuntimeException {
    private val msg: String
    private val errorCode: Int
    private val httpCode: Int

    constructor(msg: String, errorCode: Int, httpCode: Int, thrown: Throwable): super(msg, thrown) {
        this.msg = msg
        this.errorCode = errorCode
        this.httpCode = httpCode
    }
    constructor(msg: String, errorCode: Int, httpCode: Int): super(msg) {
        this.msg = msg
        this.errorCode = errorCode
        this.httpCode = httpCode
    }



    fun to(msg: String): Errors {
        return Errors(msg, this.errorCode, this.httpCode)
    }

    fun to(msg: String, cause: Throwable): Errors {
        return Errors(msg, this.errorCode, this.httpCode, cause)
    }

    fun to(cause: Throwable): Errors {
        return Errors(this.msg, this.errorCode, this.httpCode, cause)
    }
}

object CommonErrors {
    val INVALID_PARAM: Errors = Errors("非法参数", 10000400, 400)
    val ILLEGAL_STATE: Errors = Errors("非法状态", 10000500, 500)
    val INTERNAL_ERROR: Errors = Errors("内部错误", 10000500, 500)
    val RESOURCE_NOT_FOUND: Errors = Errors("访问的资源不存在", 10000404, 404)
}





