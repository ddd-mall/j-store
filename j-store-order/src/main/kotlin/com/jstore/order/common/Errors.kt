package com.jstore.com.jstore.order.common

data class Errors(val msg: String, val errorCode: Int, val httpCode: Int): RuntimeException() {
    companion object {
        object CommonlyErrors {
            val INVALID_PARAM: Errors = Errors("非法参数", 10000400, 400)
            val ILLEGAL_STATE: Errors = Errors("非法状态", 10000500, 500)
            val INTERNAL_ERROR: Errors = Errors("内部错误", 10000500, 500)
            val RESOURCE_NOT_FOUND: Errors = Errors("访问的资源不存在", 10000404, 404)
        }
    }

    fun withMsg(msg: String): Errors {
        return Errors(msg, this.errorCode, this.httpCode)
    }
}





