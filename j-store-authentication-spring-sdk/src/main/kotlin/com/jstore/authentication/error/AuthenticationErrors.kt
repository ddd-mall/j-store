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

import com.jstore.common.errors.BusinessError

object AuthenticationErrors {
    val TOKEN_MISSING = BusinessError("令牌缺失", "Auth.Token.Missing", 401)
    val TOKEN_INVALID = BusinessError("令牌无效", "Auth.Token.Invalid", 401)
    val TOKEN_REVOKED = BusinessError("令牌已被吊销", "Auth.Token.Revoked", 401)
    val INTERNAL_ERROR = BusinessError("认证服务内部错误", "Auth.InternalError", 500)
}
