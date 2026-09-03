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
package com.jstore.order.domain.order

import com.jstore.common.properties.PhoneNumber

/** 用户信息值对象 不可变，代表订单购买者的基本信息 */
data class UserInfo(
    val authenticationDomain: String,
    val uid: Long,
    val phoneNumber: PhoneNumber?, // ✅ 改为 val（不可变）
    val userName: String?, // ✅ 改为 val（不可变）
) {
    init {
        require(authenticationDomain.isNotBlank()) { "认证域不能为空" }
        require(uid > 0) { "用户ID必须大于0" }
    }
}
