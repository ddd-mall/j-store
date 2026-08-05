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

/** Token 存储接口（RefreshToken 存储 + AccessToken 黑名单） 定义在领域层，实现在基础设施层（Redis） */
interface TokenStore {
    /** 存储 RefreshToken */
    fun storeRefreshToken(userId: UserId, refreshToken: String, ttlSeconds: Long)

    /** 获取存储的 RefreshToken */
    fun getRefreshToken(userId: UserId): String?

    /** 删除 RefreshToken */
    fun removeRefreshToken(userId: UserId)

    /** 将 AccessToken 加入黑名单 */
    fun blacklistAccessToken(jti: String, ttlSeconds: Long)

    /** 检查 AccessToken 是否在黑名单中 */
    fun isAccessTokenBlacklisted(jti: String): Boolean
}
