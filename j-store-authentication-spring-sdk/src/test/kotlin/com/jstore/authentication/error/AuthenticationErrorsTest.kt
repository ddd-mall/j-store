package com.jstore.authentication.error

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

// **Validates: Requirements 6.1, 6.2**
class AuthenticationErrorsTest : FunSpec({

    test("TOKEN_MISSING has correct message, errorCode, and httpCode") {
        AuthenticationErrors.TOKEN_MISSING.message shouldBe "令牌缺失"
        AuthenticationErrors.TOKEN_MISSING.errorCode shouldBe "Auth.Token.Missing"
        AuthenticationErrors.TOKEN_MISSING.httpCode shouldBe 401
    }

    test("TOKEN_INVALID has correct message, errorCode, and httpCode") {
        AuthenticationErrors.TOKEN_INVALID.message shouldBe "令牌无效"
        AuthenticationErrors.TOKEN_INVALID.errorCode shouldBe "Auth.Token.Invalid"
        AuthenticationErrors.TOKEN_INVALID.httpCode shouldBe 401
    }

    test("TOKEN_BLACKLISTED has correct message, errorCode, and httpCode") {
        AuthenticationErrors.TOKEN_BLACKLISTED.message shouldBe "令牌已被吊销"
        AuthenticationErrors.TOKEN_BLACKLISTED.errorCode shouldBe "Auth.Token.Blacklisted"
        AuthenticationErrors.TOKEN_BLACKLISTED.httpCode shouldBe 401
    }

    test("INTERNAL_ERROR has correct message, errorCode, and httpCode") {
        AuthenticationErrors.INTERNAL_ERROR.message shouldBe "认证服务内部错误"
        AuthenticationErrors.INTERNAL_ERROR.errorCode shouldBe "Auth.InternalError"
        AuthenticationErrors.INTERNAL_ERROR.httpCode shouldBe 500
    }
})
