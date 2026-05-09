package com.jstore.authentication.error

import com.jstore.common.errors.BusinessError

object AuthenticationErrors {
    val TOKEN_MISSING = BusinessError("令牌缺失", "Auth.Token.Missing", 401)
    val TOKEN_INVALID = BusinessError("令牌无效", "Auth.Token.Invalid", 401)
    val TOKEN_BLACKLISTED = BusinessError("令牌已被吊销", "Auth.Token.Blacklisted", 401)
    val INTERNAL_ERROR = BusinessError("认证服务内部错误", "Auth.InternalError", 500)
}
