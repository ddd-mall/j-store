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
package com.jstore.user

import com.jstore.user.domain.useraccount.BCryptPasswordHasher
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Feature: user-account, Property 2: 密码哈希 round-trip
 *
 * For any 满足强度要求的明文密码字符串，经 PasswordHasher.hash() 哈希后， 再用 PasswordHasher.matches() 验证原始明文与哈希值，结果应为
 * true。
 *
 * **Validates: Requirements 1.4, 2.2, 6.1, 6.2**
 */
class BCryptPasswordHasherPropertyTest :
    FunSpec({

        // Use strength 4 (minimum) to speed up BCrypt in tests
        val hasher = BCryptPasswordHasher(strength = 4)

        // Generate random valid passwords: 8-32 chars, at least one letter and one digit
        val validPasswordArb: Arb<String> =
            Arb.int(8..32).flatMap { len ->
                val letterCount = len - 2
                Arb.bind(
                    Arb.list(Arb.char('a'..'z'), letterCount..letterCount),
                    Arb.char('0'..'9'),
                    Arb.char('0'..'9'),
                ) { letters, d1, d2 ->
                    (letters + d1 + d2).shuffled().joinToString("")
                }
            }

        test("hash then matches should return true for any valid password") {
            checkAll(100, validPasswordArb) { rawPassword ->
                val hashed = hasher.hash(rawPassword)
                hasher.matches(rawPassword, hashed) shouldBe true
            }
        }
    })
