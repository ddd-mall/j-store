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

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.*
import com.jstore.user.domain.useraccount.command.UserRegisterCMD
import com.jstore.user.domain.useraccount.event.UserAccountRegisteredEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Feature: user-account, Property 1: 注册创建不变量
 *
 * For any 合法的注册参数（有效 PhoneNumber、有效 Nickname、满足强度要求的密码）， UserAccountFactory 创建的 UserAccount 状态应为
 * ACTIVE， 且其 domainEventQueue 中应包含一个 UserAccountRegisteredEvent， 事件携带的 userId 和 phoneNumber 与聚合根一致。
 *
 * **Validates: Requirements 1.1, 1.3, 11.1**
 */
class UserAccountFactoryPropertyTest :
    FunSpec({

        // Simple PasswordHasher that returns a predictable hash
        val simpleHasher =
            object : PasswordHasher {
                override fun hash(rawPassword: String): String = "hashed_$rawPassword"

                override fun matches(rawPassword: String, hashedPassword: String): Boolean =
                    hashedPassword == "hashed_$rawPassword"
            }

        val factory = UserAccountFactoryImpl(SnowFlakSequence())

        // Generator for valid Chinese phone numbers (11 digits starting with 13x)
        val validPhoneArb: Arb<PhoneNumber> =
            Arb.int(0..99999999).map { num ->
                PhoneNumber("13${num.toString().padStart(9, '0')}")
            }

        // Generator for valid nicknames (non-blank, 1-20 chars)
        val validNicknameArb: Arb<String> = Arb.string(1..20).filter { it.isNotBlank() }

        // Generator for valid passwords (8-32 chars, at least one letter and one digit)
        val validPasswordArb: Arb<String> =
            Arb.int(8..30).flatMap { len ->
                val letterCount = len - 2
                Arb.bind(
                    Arb.list(Arb.char('a'..'z'), letterCount..letterCount),
                    Arb.char('0'..'9'),
                    Arb.char('0'..'9'),
                ) { letters, d1, d2 ->
                    (letters + d1 + d2).shuffled().joinToString("")
                }
            }

        test("factory creates UserAccount with ACTIVE status and correct registered event") {
            checkAll(100, validPhoneArb, validNicknameArb, validPasswordArb) {
                phone,
                nickname,
                password ->
                val cmd =
                    UserRegisterCMD(
                        phoneNumber = phone,
                        nickname = nickname,
                        rawPassword = password,
                    )

                val result = factory.create(cmd, simpleHasher)

                result.shouldBeInstanceOf<Success<UserAccount>>()
                val account = result.value

                // Status should be ACTIVE
                account.status shouldBe UserAccountStatus.ACTIVE

                // Phone number should match
                account.phoneNumber shouldBe phone

                // Nickname should match
                account.nickname shouldBe Nickname(nickname)

                // domainEventQueue should contain UserAccountRegisteredEvent
                val events = account.domainEventQueue.toList()
                events.size shouldBe 1
                val event = events[0]
                event.shouldBeInstanceOf<UserAccountRegisteredEvent>()
                event.userId shouldBe account.id
                event.phoneNumber shouldBe phone
            }
        }
    })
