package com.jstore.user.domain.useraccount

import com.jstore.common.errors.BusinessError

object UserAccountErrors {
    val USER_NOT_FOUND = BusinessError("用户不存在", "User.NotFound", 404)
    val PHONE_ALREADY_REGISTERED = BusinessError("手机号已注册", "User.Phone.Duplicate", 400)
    val PASSWORD_STRENGTH_INSUFFICIENT = BusinessError("密码强度不足", "User.Password.Weak", 400)
    val NICKNAME_INVALID = BusinessError("昵称无效", "User.Nickname.Invalid", 400)
    val PASSWORD_MISMATCH = BusinessError("密码错误", "User.Password.Mismatch", 400)
    val INVALID_CREDENTIALS = BusinessError("手机号或密码错误", "User.Credentials.Invalid", 401)
    val LOGIN_RATE_LIMITED = BusinessError("登录尝试过于频繁，请稍后再试", "User.Login.RateLimited", 429)
    val PHONE_VERIFICATION_INVALID =
        BusinessError("手机验证码无效或已过期", "User.PhoneVerification.Invalid", 400)
    val PHONE_VERIFICATION_RATE_LIMITED =
        BusinessError("验证码发送过于频繁，请稍后再试", "User.PhoneVerification.RateLimited", 429)
    val OLD_PASSWORD_MISMATCH = BusinessError("旧密码错误", "User.Password.OldMismatch", 400)
    val ACCOUNT_DISABLED = BusinessError("账号已禁用", "User.Account.Disabled", 403)
    val ILLEGAL_STATE = BusinessError("账号状态不合法", "User.State.Invalid", 400)
    val TOKEN_INVALID = BusinessError("令牌无效", "User.Token.Invalid", 401)
    val TOKEN_EXPIRED = BusinessError("令牌已过期", "User.Token.Expired", 401)
    val REFRESH_TOKEN_REVOKED = BusinessError("令牌已失效，请重新登录", "User.Token.Revoked", 401)
}
