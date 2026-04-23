package com.jstore.common.errors

class BusinessError(
    val message: String,
    val errorCode: String,
    val httpCode: Int,
) {

    fun msg(message: String): BusinessError {
        return BusinessError(message, this.errorCode, this.httpCode)
    }
}

object CommonBusinessError {
    val INVALID_PARAM: BusinessError = BusinessError("非法参数", "Parameters.Invalid", 400)
    val ILLEGAL_STATE: BusinessError = BusinessError("非法状态", "App.IllegalState", 500)
    val INTERNAL_ERROR: BusinessError = BusinessError("内部错误", "App.InternalError", 500)
    val CONCURRENT_CONFLICT_EXCEPTION = BusinessError("系统繁忙，请稍后重试", "App.concurrentialConflict", 500)
    val OBJECT_NOT_FOUNT = BusinessError("未能找到资源", "Biz.Object Not Found", 404)
}