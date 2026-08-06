/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.authentication.error

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

// **Validates: Requirements 6.1, 6.2**
class AuthenticationErrorsTest :
    FunSpec({
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

        test("TOKEN_REVOKED has correct message, errorCode, and httpCode") {
            AuthenticationErrors.TOKEN_REVOKED.message shouldBe "令牌已被吊销"
            AuthenticationErrors.TOKEN_REVOKED.errorCode shouldBe "Auth.Token.Revoked"
            AuthenticationErrors.TOKEN_REVOKED.httpCode shouldBe 401
        }

        test("INTERNAL_ERROR has correct message, errorCode, and httpCode") {
            AuthenticationErrors.INTERNAL_ERROR.message shouldBe "认证服务内部错误"
            AuthenticationErrors.INTERNAL_ERROR.errorCode shouldBe "Auth.InternalError"
            AuthenticationErrors.INTERNAL_ERROR.httpCode shouldBe 500
        }
    })
