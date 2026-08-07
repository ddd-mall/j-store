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

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/** BCrypt 实现的 PasswordHasher 使用 Spring Security Crypto 的 BCryptPasswordEncoder */
class BCryptPasswordHasher(strength: Int = 10) : PasswordHasher {

    private val encoder = BCryptPasswordEncoder(strength)

    override fun hash(rawPassword: String): String {
        return encoder.encode(rawPassword)!!
    }

    override fun matches(rawPassword: String, hashedPassword: String): Boolean {
        return encoder.matches(rawPassword, hashedPassword)
    }
}
