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
package com.jstore.user.domain.useraccount

/** 令牌提供者接口 定义在领域层，实现在基础设施层（JWT） */
interface TokenProvider {
    /** 签发 AccessToken，返回 token 字符串 */
    fun issueAccessToken(userId: UserId): String

    /** 签发 RefreshToken，返回 token 字符串 */
    fun issueRefreshToken(userId: UserId): String

    /** 解析 AccessToken，返回 userId；无效则返回 null */
    fun parseAccessToken(token: String): UserId?

    /** 解析 RefreshToken，返回 userId；无效则返回 null */
    fun parseRefreshToken(token: String): UserId?

    /** 获取 AccessToken 的 jti */
    fun getAccessTokenJti(token: String): String?

    /** 获取 AccessToken 的剩余有效期（秒） */
    fun getAccessTokenRemainingSeconds(token: String): Long
}
